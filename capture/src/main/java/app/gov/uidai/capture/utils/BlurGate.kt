package app.gov.uidai.capture.utils

import android.os.SystemClock

class BlurGate(
    private val targetThreshold: Float,
    private val fallbackThreshold: Float,
    private val maxWaitMs: Long
) {
    @Volatile private var windowStart: Long = 0L

    // Returns the CURRENT threshold to check against — nothing else.
    // No frame storage, no pass/fail decision, no best-score tracking.
    fun currentThreshold(): Float {
        if (windowStart == 0L) windowStart = SystemClock.elapsedRealtime()
        val elapsed = SystemClock.elapsedRealtime() - windowStart
        return if (elapsed > maxWaitMs) fallbackThreshold else targetThreshold
    }

    fun reset() {
        windowStart = 0L
    }
}