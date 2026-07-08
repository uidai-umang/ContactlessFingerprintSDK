package app.gov.uidai.capture.utils

import android.os.SystemClock
import android.util.Log

inline fun <T> logExecutionTime(tag: String?, title: String, block: () -> T): T {
    val startTime = SystemClock.uptimeMillis()
    val res = block()
    val durationMs = (SystemClock.uptimeMillis() - startTime)
    Log.d(tag, "Execution time -- $title: ${durationMs}ms")
    return res
}