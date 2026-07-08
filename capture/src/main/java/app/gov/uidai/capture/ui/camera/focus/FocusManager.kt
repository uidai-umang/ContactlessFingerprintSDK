package app.gov.uidai.capture.ui.camera.focus

import android.util.Log
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider

abstract class FocusManager(
    protected val provider: CameraContextProvider
) {
    companion object {
        private val TAG = FocusManager::class.simpleName
    }

    private var focusState = FocusState.UNLOCKED
    private var needFocusTrigger = true

    abstract fun lock(paramProvider: FocusLockParamProvider)

    abstract fun unlock()

    abstract fun setOptimalMode()

    fun isFocusLockedForCapture(): Boolean {
        return !needFocusTrigger
    }

    fun setNeedFocusTrigger(value: Boolean){
        needFocusTrigger = value
    }

    fun getNeedFocusTrigger(): Boolean {
        return needFocusTrigger
    }

    fun updateFocusState(state: FocusState){
        if(focusState != state){
            focusState = state
            Log.d(TAG, "FOCUS -- Focus state updated to $focusState")
        }
    }

    fun getFocusState(): FocusState {
        return focusState
    }
}