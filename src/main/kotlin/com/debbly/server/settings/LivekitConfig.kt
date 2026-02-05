package com.debbly.server.settings

data class LivekitConfig(
    val videoCodec: String = DEFAULT_VIDEO_CODEC,
    val simulcastLayers: List<String> = DEFAULT_SIMULCAST_LAYERS,
    val maxBitrate: Int = DEFAULT_MAX_BITRATE,
    val maxFramerate: Int = DEFAULT_MAX_FRAMERATE,
    val dynacast: Boolean = DEFAULT_DYNACAST,
    val adaptiveStream: Boolean = DEFAULT_ADAPTIVE_STREAM
) {
    companion object {
        const val DEFAULT_VIDEO_CODEC = "h264"
        val DEFAULT_SIMULCAST_LAYERS = listOf("s720", "s360")
        const val DEFAULT_MAX_BITRATE = 1_500_000
        const val DEFAULT_MAX_FRAMERATE = 30
        const val DEFAULT_DYNACAST = true
        const val DEFAULT_ADAPTIVE_STREAM = true

        val DEFAULT = LivekitConfig()
    }
}
