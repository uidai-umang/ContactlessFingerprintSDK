package app.gov.uidai.capture.ui.camera.manager

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SessionTimerManager(private val sessionTimeoutDuration: Long)  {

    companion object {
        private val TAG = SessionTimerManager::class.simpleName
    }

    private val _sessionTimeoutEvent = MutableSharedFlow<Unit>(replay = 0)
    val sessionTimeoutEvent = _sessionTimeoutEvent.asSharedFlow()

    private var _coroutineScope: CoroutineScope? = null
    private val coroutineScope: CoroutineScope
        get() = checkNotNull(_coroutineScope)

    // Session timeout management
    private var sessionStartTime: Long = 0
    private var sessionTimerJob: Job? = null
    private var currentTimeoutDuration: Long = sessionTimeoutDuration
    private var isSessionActive: Boolean = false

    fun setCoroutineScope(scope: CoroutineScope){
        _coroutineScope = scope
    }

    /**
     * Start the session timeout timer
     */
    fun startSessionTimer() {
        if (isSessionActive) {
            Log.d(TAG, "Session timer already active")
            return
        }
        sessionStartTime = System.currentTimeMillis()
        isSessionActive = true

        Log.i(TAG, "Starting session timer: ${sessionTimeoutDuration / 1000}s")
        startTimerCountdown()
    }

    /**
     * Start the actual timer countdown
     */
    private fun startTimerCountdown() {
        sessionTimerJob?.cancel()
        sessionTimerJob = coroutineScope.launch {
            try {
                Log.i(TAG, "Session timer started")
                delay(currentTimeoutDuration)
                handleTimerTimeout()
            } catch (e: Exception) {
                Log.d(TAG, "Timer cancelled: ${e.message}")
            }
        }
    }

    /**
     * Handle timer timeout - check state and either extend or timeout
     */
    private suspend fun handleTimerTimeout() {
        if (!isSessionActive) {
            Log.d(TAG, "Session no longer active, ignoring timeout")
            return
        }
        isSessionActive = false
        _sessionTimeoutEvent.emit(Unit)
    }

    /**
     * Stop the session timeout timer
     */
    fun stopSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        isSessionActive = false
        currentTimeoutDuration = sessionTimeoutDuration
        Log.i(TAG, "Session timer stopped")
    }

    fun resetSessionTimerTo(timeInMillis: Long = sessionTimeoutDuration){
        stopSessionTimer()
        currentTimeoutDuration = timeInMillis
        startSessionTimer()
    }

    /**
     * Get time eclipsed
     */
    fun getTimeElapsed(): Long {
        if (!isSessionActive) return 0
        val elapsed = System.currentTimeMillis() - sessionStartTime
        return minOf(currentTimeoutDuration, elapsed)
    }

    /**
     * Get remaining time
     */
    fun getRemainingTime(): Long {
        if (!isSessionActive) return 0
        val elapsed = System.currentTimeMillis() - sessionStartTime
        return maxOf(0, currentTimeoutDuration - elapsed)
    }

    /**
     * Check if session is currently active
     */
    fun isSessionTimerActive(): Boolean = isSessionActive
}