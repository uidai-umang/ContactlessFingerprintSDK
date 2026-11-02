package app.gov.uidai.capture.usecase.single

import android.os.SystemClock

/**
 * Smooths YOLO finger-distance readings (mm) with a median of the last [window] readings.
 * - verdict(): move closer / move back / OK, from the median (single spikes can't flip it).
 * - isGoodAndStable(): median is inside target ± tolerance (the capture gate).
 * Two misses in a row (no finger seen) clear the readings.
 */
class FingerDistanceTracker(
    @Volatile var targetMM: Float,
    private val toleranceMM: Float,
    private val maxAgeMs: Long = 3_000L,
    private val maxConsecutiveMisses: Int = 2,
    private val window: Int = 5,
    private val minReadings: Int = 3
) {
    enum class Verdict { UNKNOWN, TOO_CLOSE, TOO_FAR, OK }

    private class Reading(val mm: Float, val atMs: Long)

    private val readings = ArrayDeque<Reading>(window)
    private var misses = 0

    @Synchronized
    fun record(mm: Float?) {
        if (mm == null || mm <= 0f) {
            misses++
            if (misses >= maxConsecutiveMisses) readings.clear()
            return
        }
        misses = 0
        readings.addLast(Reading(mm, SystemClock.uptimeMillis()))
        if (readings.size > window) readings.removeFirst()
    }

    /** Median of fresh readings, or null if there aren't at least [minReadings]. */
    @Synchronized
    fun smoothedMM(): Float? {
        val now = SystemClock.uptimeMillis()
        val fresh = readings.filter { now - it.atMs <= maxAgeMs }.map { it.mm }.sorted()
        if (fresh.size < minReadings) return null
        return fresh[fresh.size / 2]
    }

    fun verdict(): Verdict {
        val mm = smoothedMM() ?: return Verdict.UNKNOWN
        return when {
            mm > targetMM + toleranceMM -> Verdict.TOO_FAR
            mm < targetMM - toleranceMM -> Verdict.TOO_CLOSE
            else -> Verdict.OK
        }
    }

    fun isGoodAndStable(): Boolean = verdict() == Verdict.OK

    @Synchronized
    fun reset() {
        readings.clear()
        misses = 0
    }
}