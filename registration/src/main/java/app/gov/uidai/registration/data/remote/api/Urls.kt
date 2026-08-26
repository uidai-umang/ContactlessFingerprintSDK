package app.gov.uidai.registration.data.remote.api

object Urls {
    const val RESIDENT_LOOKUP = "api/v1/residents/lookup"
    const val SESSION_CREATE = "api/v1/sessions"
    const val SESSION_CLOSE = "api/v1/sessions/close"
    const val CAPTURE_UPLOAD = "api/v1/captures"
    const val CAPTURE_BATCH_UPLOAD = "api/v1/captures/batch"

    const val DEVICE_REGISTER = "api/v1/devices/register"

    const val DASHBOARD_OVERVIEW = "api/v1/dashboard/overview"
    const val DASHBOARD_DIVERSITY = "api/v1/dashboard/diversity"
    const val DASHBOARD_FINGERS = "api/v1/dashboard/fingers"
    const val DASHBOARD_ALERTS = "api/v1/dashboard/alerts"
    const val QUOTA_CHECK = "api/v1/quota/check"
    const val QUOTA_OVERRIDE = "api/v1/quota/override"
}