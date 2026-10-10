package app.gov.uidai.registration.utils

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.gov.uidai.registration.model.resident.ResidentInput

sealed class Routes(val route: String) {

    data object RegisterOperator : Routes(PATH_REGISTER_OPERATOR)

    data object UidEntry : Routes(PATH_UID_ENTRY)

    data object CaptureMethod :
        Routes("$PATH_CAPTURE_METHOD/{$ARG_REF_ID}/{$ARG_DOB}/{$ARG_GENDER}") {
        fun createRoute(resident: ResidentInput) =
            "$PATH_CAPTURE_METHOD/${resident.refId}/${resident.dob}/${resident.gender}"
    }

    data object Registration :
        Routes("$PATH_REGISTRATION/{$ARG_REF_ID}/{$ARG_DOB}/{$ARG_GENDER}") {
        fun createRoute(resident: ResidentInput) =
            "$PATH_REGISTRATION/${resident.refId}/${resident.dob}/${resident.gender}"
    }

    // Placeholder if/when this screen gets rebuilt — not wired into
    // MainActivity's NavHost yet.
    data object UserInfo : Routes("$PATH_USER_INFO/{$ARG_UID_HASH}") {
        fun createRoute(uidHash: String) = "$PATH_USER_INFO/$uidHash"
    }

    data object Dashboard : Routes("$PATH_DASHBOARD/{$ARG_OPERATOR_ID}") {
        fun createRoute(operatorId: String) = "$PATH_DASHBOARD/$operatorId"
    }

    companion object {
        private const val PATH_REGISTER_OPERATOR = "register_operator"
        private const val PATH_UID_ENTRY = "uid_entry"
        private const val PATH_CAPTURE_METHOD = "capture_method"
        private const val PATH_REGISTRATION = "registration"
        private const val PATH_USER_INFO = "user_info"
        private const val PATH_DASHBOARD = "dashboard"
        const val ARG_UID_HASH = "uidHash"
        const val ARG_OPERATOR_ID = "operatorId"
        const val ARG_REF_ID = "refId"
        const val ARG_DOB = "dob"
        const val ARG_GENDER = "gender"
    }
}

val residentNavArguments: List<NamedNavArgument> = listOf(
    navArgument(Routes.ARG_REF_ID) { type = NavType.StringType },
    navArgument(Routes.ARG_DOB) { type = NavType.StringType },
    navArgument(Routes.ARG_GENDER) { type = NavType.StringType }
)

fun NavBackStackEntry.residentInput() = ResidentInput(
    refId = arguments?.getString(Routes.ARG_REF_ID).orEmpty(),
    dob = arguments?.getString(Routes.ARG_DOB).orEmpty(),
    gender = arguments?.getString(Routes.ARG_GENDER).orEmpty()
)