package app.gov.uidai.capture.ui.camera.manager

import android.util.Log
import app.gov.uidai.capture.domain.model.LiveQualityScores
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.ui.camera.model.ActiveWarning
import app.gov.uidai.capture.ui.camera.model.CaptureState
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Info
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.Warning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Manages the state of the capture UI by processing events from the capture pipeline
 * and providing a single stream of `CaptureState` updates.
 */
class CaptureStateManager @Inject constructor() {

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Initial)
    val captureState = _captureState.asStateFlow()

    // Live Stage 1 debug quality scores — independent of the capture state machine above,
    // since these update every Stage 1 cycle regardless of warn/success/fail state.
    private val _stage1QualityScores = MutableStateFlow<LiveQualityScores?>(null)
    val stage1QualityScores = _stage1QualityScores.asStateFlow()

    private val activeWarnings = mutableListOf<ActiveWarning>()
    private val stage2Errors = mutableListOf<Error>()

    private val isAccumulatingFrames = AtomicBoolean(false)
    private val isStage2Processing = AtomicBoolean(false)
    private val isStage2Passed = AtomicReference<Boolean?>(null)
    private val stage2ResultValue = AtomicReference<Stage2ResultValue?>(null)
    private val stage2ProcessingStageMessage = AtomicReference<Info?>(null)

    companion object {
        private val TAG = CaptureStateManager::class.java.simpleName

        private const val WARNING_TIMEOUT_MS = 3000L // 3 seconds
        private const val MIN_WARNING_DURATION_MS = 300L // 300ms minimum display
    }

    /**
     * Updates the capture state and notifies observers.
     */
    private fun updateCaptureState() {
        val newState = getSuggestedState()
        if (newState == _captureState.value) return

        _captureState.value = newState
        Log.d(TAG, "STATE -- New State: $newState")
    }

    private fun getSuggestedState(): CaptureState {
        val latestError = when (stage2Errors.size) {
            0 -> Error.SomethingWentWrong
            else -> stage2Errors.first()
        }
        val activeWarning = getActiveWarning()

        return when {
            isStage2Passed.get() == true -> CaptureState.Success(
                info = Info.Success,
                resultValue = stage2ResultValue.get()
            )

            isStage2Passed.get() == false -> CaptureState.Failed(
                error = latestError,
                resultValue = stage2ResultValue.get()
            )

            isStage2Processing.get() -> CaptureState.AutoCaptureSuccess(
                info = stage2ProcessingStageMessage.get() ?: Info.EvaluatingImageQuality
            )

            activeWarning != null -> CaptureState.Warn(
                warning = activeWarning
            )

            isAccumulatingFrames.get() -> CaptureState.AutoCaptureTrigger(
                info = Info.HoldStill
            )

            else -> CaptureState.Initial
        }
    }

    private fun getActiveWarning(): Warning? {
        if (activeWarnings.isNotEmpty()) {
            // Priority resolution: Highest priority first, then most recent
            val displayWarning = activeWarnings
                .sortedWith(compareBy<ActiveWarning> { it.warning.priority }
                    .thenByDescending { it.lastSeenAt })
                .firstOrNull()

            return displayWarning?.warning
        } else {
            return null
        }
    }

    /**
     * Processes the result from Stage 1.
     */
    fun reportStage1Result(
        passed: Boolean,
        newWarnings: List<Warning>,
        passedChecks: List<ProcessingStage>
    ) {
        val currentTime = System.currentTimeMillis()

        // Add or update new warnings
        newWarnings.forEach { warning ->
            val existing = activeWarnings.find { it.warning.id == warning.id }
            if (existing != null) {
                // Update existing warning
                activeWarnings.remove(existing)
                activeWarnings.add(
                    existing.copy(
                        lastSeenAt = currentTime,
                        consecutiveFailures = existing.consecutiveFailures + 1
                    )
                )
                Log.d(
                    TAG,
                    "WARNING -- Updated warning: ${warning.id}, failures: ${existing.consecutiveFailures + 1}"
                )
            } else {
                // Add new warning
                activeWarnings.add(
                    ActiveWarning(
                        warning = warning,
                        activatedAt = currentTime,
                        lastSeenAt = currentTime,
                        consecutiveFailures = 1
                    )
                )
                Log.d(
                    TAG,
                    "WARNING -- Added new warning: ${warning.id}"
                )
            }
        }

        // Clear resolved warnings (with persistence)
        passedChecks.forEach { resolvedStates ->
            val toRemove = activeWarnings.filter { activeWarning ->
                activeWarning.warning.processingStage == resolvedStates &&
                        (currentTime - activeWarning.lastSeenAt) > MIN_WARNING_DURATION_MS
            }

            toRemove.forEach { activeWarning ->
                activeWarnings.remove(activeWarning)
                Log.d(
                    TAG,
                    "WARNING -- Removed resolved warnings: ${activeWarning.warning.id}"
                )
            }
        }

        // Remove expired warnings
        val expired = activeWarnings.filter { warning ->
            (currentTime - warning.lastSeenAt) > WARNING_TIMEOUT_MS
        }

        expired.forEach { warning ->
            activeWarnings.remove(warning)
            Log.d(
                TAG,
                "WARNING -- Expired warning: ${warning.warning.id}"
            )
        }
        updateCaptureState()
    }

    /**
     * Processes the result from Stage 2.
     */
    fun reportStage2Result(
        passed: Boolean,
        errors: List<Error>,
    ) {
        isStage2Passed.set(passed)
        stage2Errors.clear()
        stage2Errors.addAll(errors)
        updateCaptureState()
    }

    fun reportStage2ProcessingStage(
        processingStage: ProcessingStage
    ){
        val message = when(processingStage){
            ProcessingStage.BLUR -> Info.EvaluatingImageQuality
            ProcessingStage.SEGMENTATION -> Info.CheckingFingerPresence
            else -> null
        }
        stage2ProcessingStageMessage.set(message)
        updateCaptureState()
    }

    fun reportStage2ResultValues(value: Stage2ResultValue) {
        stage2ResultValue.set(value)
        updateCaptureState()
    }

    fun reportStage1ResultValues(values: LiveQualityScores) {
        _stage1QualityScores.value = values
    }

    fun reportIsAccumulationHappening(value: Boolean) {
        isAccumulatingFrames.set(value)
        updateCaptureState()
    }

    fun reportIsStage2Processing(value: Boolean) {
        isStage2Processing.set(value)
        updateCaptureState()
    }

    fun currentState(): CaptureState {
        return _captureState.value
    }

    fun reset() {
        activeWarnings.clear()
        stage2Errors.clear()
        isAccumulatingFrames.set(false)
        isStage2Processing.set(false)
        isStage2Passed.set(null)
        stage2ResultValue.set(null)
        stage2ProcessingStageMessage.set(null)
        _stage1QualityScores.value = null
        updateCaptureState()
    }
}
