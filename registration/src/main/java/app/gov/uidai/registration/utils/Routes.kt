package app.gov.uidai.registration.utils

sealed class Routes(val route: String) {

    data object UidEntry : Routes(PATH_UID_ENTRY)

    data object Registration : Routes("$PATH_REGISTRATION/{$ARG_UID_HASH}") {
        fun createRoute(uidHash: String) = "$PATH_REGISTRATION/$uidHash"
    }

    // Placeholder if/when this screen gets rebuilt — not wired into
    // MainActivity's NavHost yet.
    data object UserInfo : Routes("$PATH_USER_INFO/{$ARG_UID_HASH}") {
        fun createRoute(uidHash: String) = "$PATH_USER_INFO/$uidHash"
    }

    companion object {
        private const val PATH_UID_ENTRY = "uid_entry"
        private const val PATH_REGISTRATION = "registration"
        private const val PATH_USER_INFO = "user_info"
        const val ARG_UID_HASH = "uidHash"
    }
}