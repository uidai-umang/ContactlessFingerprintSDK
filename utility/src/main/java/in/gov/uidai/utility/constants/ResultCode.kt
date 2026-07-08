package `in`.gov.uidai.utility.constants

interface ResultCode {
    companion object {
        const val SDK_SUCCESS = 9000
        const val SDK_FAILED = 9001
        const val SDK_TIMEOUT = 9002
        const val SDK_USER_ABORT = 9003

        const val CAPTURE_SUCCESS = 10000
        const val CAPTURE_FAILED = 10001
        const val CAPTURE_TIMEOUT = 10002
        const val CAPTURE_USER_ABORT = 10003
    }
}