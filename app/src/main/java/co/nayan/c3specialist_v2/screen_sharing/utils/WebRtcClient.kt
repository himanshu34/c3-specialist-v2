package co.nayan.c3specialist_v2.screen_sharing.utils

import android.content.Context
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.screen_sharing.config.UserStatus
import co.nayan.c3specialist_v2.screen_sharing.models.PeerConnectionParameters
import co.nayan.c3specialist_v2.screen_sharing.models.RoomChannelResponse
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.JsonObject
import com.hosopy.actioncable.ActionCable
import com.hosopy.actioncable.Channel
import com.hosopy.actioncable.Consumer
import com.hosopy.actioncable.Subscription
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import timber.log.Timber
import java.net.URI
import javax.inject.Inject

class WebRtcClient @Inject constructor(
    private val context: Context,
    sharedStorage: SharedStorage,
    private val mPreferenceHelper: PreferenceHelper
) {

    private var isFirstTimeScreenSharing = true
    var isSharingScreen: Boolean = false
    var isAdmin: Boolean = false

    private val localUserId = sharedStorage.getUserProfileInfo()?.id.toString()
    var localStatus: String = UserStatus.CONNECTING

    var remoteStatus: String = UserStatus.DISCONNECTED
    var remoteId: String? = null
    var remoteName: String? = null

    var capturer: VideoCapturer? = null
    var peerConnectionParams: PeerConnectionParameters? = null

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val peers = hashMapOf<String, Peer>()
    private val endPoints = BooleanArray(MAX_PEER)
    private val messageHandler = MessageHandler()
    private val iceServers = mutableListOf<IceServer>()
    private val peerConnectionConstraints = MediaConstraints()
    private var videoSource: VideoSource? = null
    private var localMediaStream: MediaStream? = null

    private var consumer: Consumer? = null
    private val channelIdentifier = "LobbyChannel"
    private var subscription: Subscription? = null
    private var isSubscribed = false
    private var rtcListener: RtcListener? = null

    init {
        initConnection()
    }

    private fun initConnection() {
        try {
            val options = Consumer.Options()
            options.query = getHeaders()
            options.reconnection = true
            consumer =
                ActionCable.createConsumer(URI(BuildConfig.C3_SPECIALIST_WEBRTC_HOST), options)
            subscription = consumer?.subscriptions?.create(Channel(channelIdentifier))

            subscription?.onConnected {
                Timber.tag(TAG).e("Socket State Connect")
                isSubscribed = true
                localStatus = UserStatus.CONNECTED
                rtcListener?.onLocalStatusChanged()
            }
            subscription?.onDisconnected {
                Timber.tag(TAG).e("Socket State Disconnect")
                isSubscribed = false
                localStatus = UserStatus.DISCONNECTED
                rtcListener?.onLocalStatusChanged()
                // send a notification back to remote user ->  call failed
            }
            subscription?.onFailed {
                Timber.tag(TAG).e("Socket State Error")
                isSubscribed = false
                localStatus = UserStatus.DISCONNECTED
                rtcListener?.onLocalStatusChanged()
                // send a notification back to remote user ->  call failed
            }
            subscription?.onReceived(messageHandler.onReceived)
            consumer?.connect()
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        }
    }

    private fun notifyRemoteUser() {
        subscribedAction {
            val message = JsonObject().apply {
                addProperty("to", remoteId)
                addProperty("from", localUserId)
            }
            subscription?.perform("connected", message)
            Timber.tag(TAG).d("Socket send connection message to server.")
        }
    }

    private fun sendMessage(to: String, type: String?, payload: JSONObject) {
        subscribedAction {
            val message = JsonObject().apply {
                addProperty("to", to)
                addProperty("from", localUserId)
                addProperty("type", type)
                addProperty("payload", payload.toString())
            }
            subscription?.perform("message", message)
            Timber.tag(TAG).d("Socket send $type to $to payload: $payload")
        }
    }

    inner class MessageHandler {
        private val commandMap: HashMap<String, Command> = hashMapOf()

        init {
            commandMap["init"] = CreateOfferCommand()
            commandMap["offer"] = CreateAnswerCommand()
            commandMap["answer"] = SetRemoteSDPCommand()
            commandMap["candidate"] = AddIceCandidateCommand()
            commandMap["screenSharing"] = ScreenSharingCommand()
            commandMap["callEnd"] = CallEndCommand()
            commandMap["rejectCall"] = RejectCallCommand()
        }

        val onReceived = Subscription.ReceivedCallback {
            Timber.tag(TAG).e(it.toString())
            RoomChannelResponse.create(it)?.let { data ->
                val from = data.from
                val type = data.type

                Timber.tag(TAG).d("Socket received $type from $from")

                var payload: JSONObject? = null
                if (type != "init") {
                    payload = JSONObject(data.payload)
                }

                if (peers.containsKey(from).not()) {
                    val endPoint = findEndPoint()
                    if (endPoint != MAX_PEER) {
                        val peer = addPeer(from, endPoint)
                        peer.peerConnection?.addStream(localMediaStream)
                        commandMap[type]?.execute(from, payload)
                    }
                } else {
                    val command = commandMap[type]
                    if (payload != null) {
                        command?.execute(from, payload)
                    }
                }
            }
        }
    }

    private fun getHeaders(): MutableMap<String, String> {
        val map = hashMapOf<String, String>()
        map["content-type"] = "application/json"
        map["token-type"] = "Bearer"
        mPreferenceHelper.getAuthenticationHeaders()?.let {
            map["access-token"] = it.access_token
            map["client"] = it.client
            map["expiry"] = it.expiry
            map["uid"] = it.uid
        }
        return map
    }

    private fun initStream() {
        localMediaStream = peerConnectionFactory?.createLocalMediaStream("ARDAMS")
    }

    private fun addAudioTrack() {
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localMediaStream?.addTrack(
            peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        )
    }

    private fun addVideoTrack() {
        val videoCapturer = capturer ?: return

        videoSource = peerConnectionFactory?.createVideoSource(videoCapturer.isScreencast)
        videoCapturer.startCapture(1080, 2160, 0)
        localMediaStream?.videoTracks?.firstOrNull()?.let {
            localMediaStream?.removeTrack(it)
        }

        val localVideoTrack =
            peerConnectionFactory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localMediaStream?.addTrack(localVideoTrack)

        if (remoteStatus == UserStatus.CONNECTED) {
            onVideoTrackUpdate(true)
        }
    }

    fun removeVideoTrack() {
        isSharingScreen = false
        capturer = null
        localMediaStream?.videoTracks?.firstOrNull()?.let {
            localMediaStream?.removeTrack(it)
            onVideoTrackUpdate(false)
        }
    }

    private fun onVideoTrackUpdate(isSharing: Boolean) {
        try {
            remoteId?.let {
                val payload = JSONObject()
                payload.put("type", "screenSharing")
                payload.put("isAdded", isSharing)
                Timber.tag(CustomPeerConnectionObserver.TAG).d("onAddStream")
                sendMessage(it, "screenSharing", payload)
            }
        } catch (e: JSONException) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        }
    }

    fun startAudioStream() {
        localMediaStream?.audioTracks?.firstOrNull()?.setEnabled(true)
    }

    fun stopAudioStream() {
        localMediaStream?.audioTracks?.firstOrNull()?.setEnabled(false)
    }

    inner class Peer(val id: String, val endPoint: Int) : SdpObserver,
        CustomPeerConnectionObserver() {
        var peerConnection: PeerConnection? = null

        init {
            peerConnection = peerConnectionFactory?.createPeerConnection(iceServers, this)
            peerConnection?.addStream(localMediaStream)
        }

        override fun onSetFailure(p0: String?) {}
        override fun onSetSuccess() {}
        override fun onCreateSuccess(sdp: SessionDescription?) {
            try {
                val payload = JSONObject()
                payload.put("type", sdp?.type?.canonicalForm())
                payload.put("sdp", sdp?.description)
                Timber.tag(TAG).d("onCreateSuccess")
                sendMessage(id, sdp?.type?.canonicalForm(), payload)
                peerConnection?.setLocalDescription(this, sdp)
            } catch (e: JSONException) {
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
            }
        }

        override fun onCreateFailure(p0: String?) {}

        override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
            Timber.e("IceCandidateState : $iceConnectionState")
            when (iceConnectionState) {
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    remoteStatus = UserStatus.DISCONNECTED
                    rtcListener?.onRemoteDisconnected()
                }

                PeerConnection.IceConnectionState.CONNECTED -> {
                    remoteStatus = UserStatus.CONNECTED
                    if (isAdmin.not()) {
                        onVideoTrackUpdate(true)
                    }
                    rtcListener?.onRemoteConnected()
                }

                else -> {
                }
            }
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            try {
                val payload = JSONObject()
                payload.put("label", candidate?.sdpMLineIndex)
                payload.put("id", candidate?.sdpMid)
                payload.put("candidate", candidate?.sdp)

                Timber.tag(TAG).d("onIceCandidate")

                sendMessage(id, "candidate", payload)
            } catch (e: JSONException) {
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
            }
        }

        override fun onAddStream(mediaStream: MediaStream?) {
            Timber.tag(TAG).d("onAddStream")
            rtcListener?.onAddRemoteStream(mediaStream, endPoint + 1)
        }

        override fun onRemoveStream(mediaStream: MediaStream?) {
            Timber.tag(TAG).d("onRemoveStream")
            removePeer(id)
        }

        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
            Timber.tag(TAG).e("${p0.toString()}, ${p1?.size}")
        }

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
            Timber.tag(TAG).e(p0.toString())
        }

        override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {
            /*if (signalingState?.name == "CLOSED") {
                rtcListener?.onHungUp()
            }*/
        }
    }

    private fun addPeer(id: String, endPoint: Int): Peer {
        val peer = Peer(id, endPoint)
        peers[id] = peer
        endPoints[endPoint] = true
        return peer
    }

    private fun removePeer(id: String) {
        peers[id]?.let {
            it.peerConnection?.close()
            peers.remove(it.id)
            endPoints[it.endPoint] = false
        }
    }

    private fun findEndPoint(): Int {
        for (i in 0 until MAX_PEER) {
            if (!endPoints[i]) {
                return i
            }
        }
        return MAX_PEER
    }

    interface Command {
        fun execute(peerId: String, payload: JSONObject?)
    }

    inner class CreateOfferCommand : Command {
        override fun execute(peerId: String, payload: JSONObject?) {
            try {
                Timber.tag(TAG).d("CreateOfferCommand")
                val peer = peers[peerId]
                peer?.peerConnection?.createOffer(peer, peerConnectionConstraints)
            } catch (e: JSONException) {
                Firebase.crashlytics.recordException(e)
                Timber.e(e)
            }
        }
    }

    inner class CreateAnswerCommand : Command {
        override fun execute(peerId: String, payload: JSONObject?) {
            Timber.tag(TAG).d("CreateAnswerCommand")
            val peer = peers[peerId]
            val sdp = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(payload?.optString("type")),
                payload?.optString("sdp")
            )
            peer?.peerConnection?.setRemoteDescription(peer, sdp)
            peer?.peerConnection?.createAnswer(peer, peerConnectionConstraints)
        }
    }

    inner class SetRemoteSDPCommand : Command {
        override fun execute(peerId: String, payload: JSONObject?) {
            Timber.tag(TAG).d("SetRemoteSDPCommand")
            val peer = peers[peerId]
            val sdp = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(payload?.optString("type")),
                payload?.optString("sdp")
            )
            peer?.peerConnection?.setRemoteDescription(peer, sdp)
        }
    }

    inner class AddIceCandidateCommand : Command {
        override fun execute(peerId: String, payload: JSONObject?) {
            Timber.tag(TAG).d("AddIceCandidateCommand")
            val peerConnection = peers[peerId]?.peerConnection
            if (peerConnection?.remoteDescription != null) {
                payload?.let {
                    val candidate = IceCandidate(
                        payload.optString("id"),
                        payload.optInt("label"),
                        payload.optString("candidate")
                    )
                    peerConnection.addIceCandidate(candidate)
                }
            }
        }
    }

    inner class ScreenSharingCommand : Command {
        override fun execute(peerId: String, payload: JSONObject?) {
            Timber.tag(TAG).d("ScreenSharingCommand")
            val peerConnection = peers[peerId]?.peerConnection
            if (peerConnection?.remoteDescription != null) {
                payload?.let {
                    rtcListener?.onScreenSharing(payload.optBoolean("isAdded"))
                }
            }
        }
    }

    inner class CallEndCommand : Command {
        override fun execute(peerId: String, payload: JSONObject?) {
            Timber.tag(TAG).d("CallEndCommand")
            payload?.let {
                rtcListener?.onHungUp()
            }
        }
    }

    inner class RejectCallCommand : Command {
        override fun execute(peerId: String, payload: JSONObject?) {
            Timber.tag(TAG).d("RejectCallCommand")
            payload?.let {
                rtcListener?.onCallReject()
            }
        }
    }

    fun hungUp() {
        if (remoteStatus == UserStatus.CONNECTED) {
            remoteId?.let {
                val payload = JSONObject()
                payload.put("type", "callEnd")
                sendMessage(it, "callEnd", payload)
            }
        }
    }

    fun disconnect() {
        for (peer in peers.values) {
            peer.peerConnection?.dispose()
        }
        peerConnectionFactory?.dispose()
        videoSource?.dispose()
        consumer?.disconnect()
    }

    fun setRtcListener(toSet: RtcListener) {
        rtcListener = toSet
    }

    private fun subscribedAction(action: () -> Unit) {
        if (isSubscribed) {
            action()
        }
    }

    private fun setUpPeerConnectionInformation() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        iceServers.clear()
        iceServers.add(IceServer("stun:23.21.150.121"))
        iceServers.add(IceServer("stun:stun.l.google.com:19302"))

        peerConnectionConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        peerConnectionConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        peerConnectionConstraints.optional.add(
            MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true")
        )
    }

    fun onScreenSharingStarted(screenCapturer: VideoCapturer?) {
        isSharingScreen = true
        capturer = screenCapturer

        if (isFirstTimeScreenSharing) {
            isFirstTimeScreenSharing = false
            setUpPeerConnectionInformation()
            initStream()
            addAudioTrack()
            addVideoTrack()
            notifyRemoteUser()
        } else {
            addVideoTrack()
        }
    }

    fun setupAdmin() {
        setUpPeerConnectionInformation()
        initStream()
        addAudioTrack()
    }

    fun rejectCall() {
        remoteId?.let {
            val payload = JSONObject()
            payload.put("type", "rejectCall")
            sendMessage(it, "rejectCall", payload)
        }
    }

    fun inCallStatusUpdate() {
        subscribedAction {
            val message = JsonObject().apply {
                addProperty("from", localUserId)
                addProperty("in_call", true)
            }
            subscription?.perform("call_active", message)
            Timber.tag(TAG).d("Socket send In call message to server.")
        }
    }

    companion object {
        private const val TAG = "WebRtcClient"
        private const val MAX_PEER = 1
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
    }

    interface RtcListener {
        fun onAddRemoteStream(mediaStream: MediaStream?, endPoint: Int)
        fun onLocalStatusChanged()
        fun onRemoteConnected()
        fun onRemoteDisconnected()
        fun onScreenSharing(isAdded: Boolean)
        fun onHungUp()
        fun onCallReject()
    }
}