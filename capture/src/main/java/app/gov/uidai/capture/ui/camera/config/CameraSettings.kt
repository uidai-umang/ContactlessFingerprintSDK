package app.gov.uidai.capture.ui.camera.config

import android.hardware.camera2.CameraCharacteristics
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType
import app.gov.uidai.capture.ui.camera.focus.FocusType

object CameraSettings : PreferenceGroup {
    override val title: String
        get() = "# Camera"

    val CAMERA_FACING = PreferenceParam(
        key = "camera.lens_facing",
        displayName = "Camera Lens Facing",
        type = PreferenceType.INT,
        defaultValue = CameraCharacteristics.LENS_FACING_BACK
    )

    val TORCH_ON = PreferenceParam(
        "camera.torch_on",
        "Torch On",
        PreferenceType.BOOLEAN,
        false
    )

    val FOCUS_TYPE = PreferenceParam(
        "camera.focus_type",
        "Focus Type",
        PreferenceType.CHOICE(options = FocusType.entries),
        FocusType.ManualFocusAtFingerDistance
    )

    val MANUAL_FOCUS_DISTANCE = PreferenceParam(
        "camera.manual_focus_distance",
        "Manual Focus Distance (m)",
        PreferenceType.FLOAT,
        0.1f
    )

    val MANUAL_CAPTURE = PreferenceParam(
        "camera.manual_capture",
        "Manual Capture",
        PreferenceType.BOOLEAN,
        false
    )

    val AVERAGE_FINGER_WIDTH_MM = PreferenceParam(
        "camera.average_finger_width",
        "Average Finger Width (mm)",
        PreferenceType.FLOAT,
        9.5f
    )

    val AVERAGE_HAND_WIDTH_MM = PreferenceParam(
        "camera.average_hand_width",
        "Average Hand Width — 4 fingers (mm)",
        PreferenceType.FLOAT,
        85f // adult hand width across index-to-little finger, held flat, in mm
    )

    val MANUAL_AE_SETTINGS = PreferenceParam(
        "camera.MANUAL_AE_SETTINGS",
        "Manual AE Settings",
        PreferenceType.BOOLEAN,
        false
    )

    val ISO = PreferenceParam(
        "camera.ISO",
        "ISO",
        PreferenceType.INT,
        100
    )

    val SHUTTER_SPEED = PreferenceParam(
        "camera.SHUTTER_SPEED",
        "Shutter Speed (ns)",
        PreferenceType.FLOAT,
        1000000f
    )
}