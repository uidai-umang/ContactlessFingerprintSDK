package app.gov.uidai.registration.data.remote.api

object Urls {
    const val PRIMARY_END_POINT = "clf/"
    const val RESIDENT_LOOKUP = "${PRIMARY_END_POINT}v1/residents/lookup"
    const val CAPTURE_UPLOAD = "${PRIMARY_END_POINT}v1/captures"
    const val CAPTURE_BATCH_UPLOAD = "${PRIMARY_END_POINT}v1/captures/batch"

    const val DEVICE_REGISTER = "${PRIMARY_END_POINT}v1/devices/register"

    const val DASHBOARD_OVERVIEW = "${PRIMARY_END_POINT}v1/dashboard/overview"
    const val DASHBOARD_DIVERSITY = "${PRIMARY_END_POINT}v1/dashboard/diversity"
    const val DASHBOARD_FINGERS = "${PRIMARY_END_POINT}v1/dashboard/fingers"
    const val DASHBOARD_ALERTS = "${PRIMARY_END_POINT}v1/dashboard/alerts"
    const val QUOTA_CHECK = "${PRIMARY_END_POINT}v1/quota/check"
    const val QUOTA_OVERRIDE = "${PRIMARY_END_POINT}v1/quota/override"
}