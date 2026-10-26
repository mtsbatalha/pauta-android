package app.pauta.benchmark

/**
 * Acumula métricas de desempenho da transcrição.
 *
 * RTF (Real-Time Factor) = tempo de decodificação / tempo de áudio.
 * RTF < 1 significa que a transcrição é mais rápida que o tempo real.
 */
class RtfTracker {

    private var segments = 0
    private var audioSecondsTotal = 0.0
    private var decodeSecondsTotal = 0.0

    fun record(audioSeconds: Double, decodeSeconds: Double) {
        require(audioSeconds >= 0.0) { "audioSeconds não pode ser negativo" }
        require(decodeSeconds >= 0.0) { "decodeSeconds não pode ser negativo" }
        segments++
        audioSecondsTotal += audioSeconds
        decodeSecondsTotal += decodeSeconds
    }

    fun reset() {
        segments = 0
        audioSecondsTotal = 0.0
        decodeSecondsTotal = 0.0
    }

    val isEmpty: Boolean get() = segments == 0

    val segmentsCount: Int get() = segments

    val totalAudioSeconds: Double get() = audioSecondsTotal

    val totalDecodeSeconds: Double get() = decodeSecondsTotal

    /** RTF médio ponderado; 0.0 quando não há amostras. */
    val averageRtf: Double
        get() = if (audioSecondsTotal > 0.0) decodeSecondsTotal / audioSecondsTotal else 0.0
}
