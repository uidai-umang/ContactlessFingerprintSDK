package app.gov.uidai.capture.utils

// A small sliding window tracking recent pass/fail history for ONE check.
// Smooths out single-frame noise (hand tremor, momentary flicker) while
// still detecting genuine sustained problems.
class RollingConfidence(
    private val windowSize: Int = 10,
    private val requiredPassRate: Float = 0.7f
) {
    private val history = ArrayDeque<Boolean>(windowSize)

    @Synchronized
    fun record(passed: Boolean) {
        if (history.size >= windowSize) history.removeFirst()
        history.addLast(passed)
    }

    @Synchronized
    fun isConfident(): Boolean {
        if (history.isEmpty()) return false
        val passRate = history.count { it } / history.size.toFloat()
        return passRate >= requiredPassRate
    }

    @Synchronized
    fun reset() = history.clear()
}