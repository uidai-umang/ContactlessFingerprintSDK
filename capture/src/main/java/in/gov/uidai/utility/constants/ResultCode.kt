package `in`.gov.uidai.utility.constants

interface ResultCode {
    companion object {
        const val CAPTURE_SUCCESS = 10000
        const val CAPTURE_FAILED = 10001
        const val CAPTURE_USER_ABORT = 10003
    }
}
