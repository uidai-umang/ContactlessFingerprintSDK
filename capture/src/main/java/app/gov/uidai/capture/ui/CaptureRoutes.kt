package app.gov.uidai.capture.ui

sealed class CaptureRoutes(val route: String) {

    data object Guideline : CaptureRoutes("$PATH_GUIDELINE/{$ARG_TXN_ID}") {
        fun createRoute(txnId: String) = "$PATH_GUIDELINE/$txnId"
    }

    data object Camera : CaptureRoutes("$PATH_CAMERA/{$ARG_TXN_ID}/{$ARG_FINGER_TYPE}") {
        fun createRoute(txnId: String, fingerType: String) = "$PATH_CAMERA/$txnId/$fingerType"
    }

    data object SlapCamera : CaptureRoutes("$PATH_SLAP_CAMERA/{$ARG_TXN_ID}/{$ARG_HAND_TYPE}") {
        fun createRoute(txnId: String, handType: String) = "$PATH_SLAP_CAMERA/$txnId/$handType"
    }

    data object DebugSettings : CaptureRoutes(PATH_DEBUG_SETTINGS)

    companion object {
        private const val PATH_GUIDELINE = "guideline"
        private const val PATH_CAMERA = "camera"
        private const val PATH_SLAP_CAMERA = "slap_camera"
        private const val PATH_DEBUG_SETTINGS = "debug_settings"
        const val ARG_TXN_ID = "txnId"
        const val ARG_FINGER_TYPE = "finger_type"
        const val ARG_HAND_TYPE = "hand_type"
    }
}