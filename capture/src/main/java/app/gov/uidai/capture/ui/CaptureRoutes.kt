package app.gov.uidai.capture.ui

sealed class CaptureRoutes(val route: String) {

    data object Guideline : CaptureRoutes("$PATH_GUIDELINE/{$ARG_TXN_ID}") {
        fun createRoute(txnId: String) = "$PATH_GUIDELINE/$txnId"
    }

    data object Camera : CaptureRoutes("$PATH_CAMERA/{$ARG_TXN_ID}") {
        fun createRoute(txnId: String) = "$PATH_CAMERA/$txnId"
    }

    data object DebugSettings : CaptureRoutes(PATH_DEBUG_SETTINGS)

    companion object {
        private const val PATH_GUIDELINE = "guideline"
        private const val PATH_CAMERA = "camera"
        private const val PATH_DEBUG_SETTINGS = "debug_settings"
        const val ARG_TXN_ID = "txnId"
    }
}