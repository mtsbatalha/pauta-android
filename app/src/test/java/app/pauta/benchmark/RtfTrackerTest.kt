package app.pauta.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtfTrackerTest {

    @Test
    fun `rtf medio ponderado sobre varios segmentos`() {
        val tracker = RtfTracker()
        tracker.record(audioSeconds = 10.0, decodeSeconds = 2.0)
        tracker.record(audioSeconds = 5.0, decodeSeconds = 1.0)

        assertEquals(2, tracker.segmentsCount)
        assertEquals(15.0, tracker.totalAudioSeconds, 1e-9)
        assertEquals(3.0, tracker.totalDecodeSeconds, 1e-9)
        // (2 + 1) / (10 + 5) = 0.2
        assertEquals(0.2, tracker.averageRtf, 1e-9)
        assertFalse(tracker.isEmpty)
    }

    @Test
    fun `sem amostras rtf e zero e tracker vazio`() {
        val tracker = RtfTracker()
        assertTrue(tracker.isEmpty)
        assertEquals(0.0, tracker.averageRtf, 1e-9)
        assertEquals(0, tracker.segmentsCount)
    }

    @Test
    fun `reset zera o acumulado`() {
        val tracker = RtfTracker()
        tracker.record(audioSeconds = 4.0, decodeSeconds = 1.0)
        tracker.reset()
        assertTrue(tracker.isEmpty)
        assertEquals(0.0, tracker.totalAudioSeconds, 1e-9)
        assertEquals(0.0, tracker.totalDecodeSeconds, 1e-9)
    }

    @Test
    fun `rejeita valores negativos`() {
        val tracker = RtfTracker()
        val error1 = runCatching { tracker.record(-1.0, 0.5) }.exceptionOrNull()
        val error2 = runCatching { tracker.record(1.0, -0.5) }.exceptionOrNull()
        assertTrue(error1 is IllegalArgumentException)
        assertTrue(error2 is IllegalArgumentException)
        // nada foi registrado
        assertTrue(tracker.isEmpty)
    }
}
