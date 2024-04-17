package com.nayan.nayancamv2.extcam.dashcam


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nayan.nayancamv2.extcam.media.VideoStreamDecoder
import com.nayan.nayancamv2.model.SocketStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

object SocketServer {

    private lateinit var ddpaiSocket: Socket
    private var isRunning: Boolean = false
    private val connectionJob = CoroutineScope(Dispatchers.IO)
    private val _socketStatus = MutableLiveData<SocketStatus>()
    val socketStatus: LiveData<SocketStatus> = _socketStatus
    val videoStreamDecoders: MutableList<VideoStreamDecoder?> = mutableListOf()

    fun startServer() {
        connectionJob.launch {
            try {
                delay(500)
                _socketStatus.postValue(SocketStatus.Started)
                ddpaiSocket = Socket()
                ddpaiSocket.connect(InetSocketAddress("193.168.0.1", 6200), 60000)
                isRunning = true
            } catch (e: Exception) {
                _socketStatus.postValue(SocketStatus.Unsuccessful)
                e.printStackTrace()
                isRunning = false
                return@launch
            }

            while (isRunning) {
                handleClient(ddpaiSocket)
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        if (!isRunning) return

        try {
            val inputStream = clientSocket.getInputStream()
            val buffer = ByteArray(307200)
            val bytesRead = inputStream.read(buffer)
            if (bytesRead != -1) {
                synchronized(this) {
                    if (_socketStatus.value != SocketStatus.Connected && _socketStatus.value != SocketStatus.Unsuccessful)
                        _socketStatus.postValue(SocketStatus.Connected)
                }
                for (videoStreamDecoder in videoStreamDecoders) {
                    videoStreamDecoder?.parse(buffer, bytesRead)
                }
            }
        } catch (e: Exception) {
            isRunning = false
            startServer()
            e.printStackTrace()
        }
    }

    fun stopServer() {
        if (::ddpaiSocket.isInitialized && ddpaiSocket.isConnected) {
            ddpaiSocket.close()
            isRunning = false
        }
        _socketStatus.postValue(SocketStatus.Stopped)
    }
}