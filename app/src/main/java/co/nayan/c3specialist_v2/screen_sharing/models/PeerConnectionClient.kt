package co.nayan.c3specialist_v2.screen_sharing.models

data class DataChannelParameters(
    val ordered: Boolean = false,
    val maxRetransmitTimeMs: Int = 0,
    val maxRetransmits: Int = 0,
    val protocol: String? = null,
    val negotiated: Boolean = false,
    val id: Int = 0
)

data class PeerConnectionParameters(
    val videoCallEnabled: Boolean = false,
    val loopback: Boolean = false,
    val tracing: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoFps: Int = 0,
    val videoMaxBitrate: Int = 0,
    val videoCodec: String? = null,
    val videoCodecHwAcceleration: Boolean = false,
    val videoFlexfecEnabled: Boolean = false,
    val audioStartBitrate: Int = 0,
    val audioCodec: String? = null,
    val noAudioProcessing: Boolean = false,
    val aecDump: Boolean = false,
    val useOpenSLES: Boolean = false,
    val disableBuiltInAEC: Boolean = false,
    val disableBuiltInAGC: Boolean = false,
    val disableBuiltInNS: Boolean = false,
    val enableLevelControl: Boolean = false,
    val disableWebRtcAGCAndHPF: Boolean = false,
    val dataChannelParameters: DataChannelParameters? = null
)