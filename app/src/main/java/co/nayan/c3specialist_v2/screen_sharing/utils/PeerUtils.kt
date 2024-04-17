package co.nayan.c3specialist_v2.screen_sharing.utils

import org.webrtc.*
import timber.log.Timber

open class CustomPeerConnectionObserver : PeerConnection.Observer {
    override fun onIceCandidate(candidate: IceCandidate?) {
        Timber.tag(TAG).e("onIceCandidate")
    }
    override fun onDataChannel(dataChannel: DataChannel?) {
        Timber.tag(TAG).e("onDataChannel")
    }
    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Timber.tag(TAG).e("onIceConnectionReceivingChange")
    }
    override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
        Timber.tag(TAG).e("onIceConnectionChange")
    }
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Timber.tag(TAG).e("onIceGatheringChange")
    }
    override fun onAddStream(mediaStream: MediaStream?) {
        Timber.tag(TAG).e("onAddStream")
    }
    override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {
        Timber.tag(TAG).e("onSignalingChange")
    }
    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Timber.tag(TAG).e("onIceCandidatesRemoved")
    }
    override fun onRemoveStream(mediaStream: MediaStream?) {
        Timber.tag(TAG).e("onRemoveStream")
    }
    override fun onRenegotiationNeeded() {
        Timber.tag(TAG).e("onRenegotiationNeeded")
    }
    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Timber.tag(TAG).e("onAddTrack")
    }

    companion object {
        const val TAG = "WebRtcClient"
    }
}