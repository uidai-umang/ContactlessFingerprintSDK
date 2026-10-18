package app.gov.uidai.capture.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Debug only. Prints WHERE the main thread is stuck whenever it is blocked > 250ms. */
object MainThreadWatchdog {
    private const val TAG = "FREEZE_WATCHDOG"
    private const val SAMPLE_MS = 250L
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        val main = Handler(Looper.getMainLooper())
        val mainThread = Looper.getMainLooper().thread
        Thread({
            while (running) {
                val latch = CountDownLatch(1)
                val sent = SystemClock.uptimeMillis()
                main.post { latch.countDown() }
                var samples = 0
                while (!latch.await(SAMPLE_MS, TimeUnit.MILLISECONDS)) {
                    if (++samples <= 40) {
                        Log.w(
                            TAG,
                            "MAIN BLOCKED ${SystemClock.uptimeMillis() - sent}ms, sample #$samples\n" +
                                    mainThread.stackTrace.take(25).joinToString("\n") { "    at $it" }
                        )
                    }
                }
                if (samples > 0) {
                    Log.w(TAG, "MAIN freed after ${SystemClock.uptimeMillis() - sent}ms")
                }
                Thread.sleep(100)
            }
        }, "freeze-watchdog").apply { isDaemon = true }.start()
    }

    fun stop() { running = false }
}