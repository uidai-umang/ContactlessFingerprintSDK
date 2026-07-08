package app.gov.uidai.capture.ui.camera.provider

import android.hardware.camera2.params.MeteringRectangle

interface FocusLockParamProvider {
    fun getMeteringRectangle(): MeteringRectangle?
    fun getFingerDistance(): Float
    fun getManualDistance(): Float
}