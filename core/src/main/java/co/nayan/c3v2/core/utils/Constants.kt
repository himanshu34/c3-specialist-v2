package co.nayan.c3v2.core.utils

class Constants {

    object ApiResponseCode {
        const val OKAY = 200
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val DUPLICATE = 412
        const val ATTENDANCE_LOCKED = 423
        const val UNKNOWN_HOST = 999
        const val BAD_GATEWAY = 502
        const val SERVICE_UNAVAILABLE = 503
        const val SERVER_STORAGE_FULL = 507
        val CLIENT_ERROR = 400..499
        val SERVER_ERROR = 500..599
    }

    object LocationService {
        const val FETCHING_LOCATION_STARTED = 1
        const val FETCHING_LOCATION_COMPLETE = 2
        const val FETCHING_LOCATION_ERROR = 3
        const val LOCATION_UNAVAILABLE = 4
    }

    object Error {
        const val ON_SUCCESS = "ON_SUCCESS"
        const val INTERNAL_ERROR_TAG = "INTERNAL_SERVER_ERROR"
        const val UNAUTHORIZED_TAG = "UNAUTHORIZED"
        const val NOT_FOUND_TAG = "NOT_FOUND"
        const val DATABASE_ERROR_TAG = "DATABASE_ERROR"
        const val SERVER_STORAGE_FULL_TAG = "SERVER_STORAGE_FULL_ERROR"
        const val MARKER_TAG_DRIVERS ="MARKER_TAG_DRIVERS"
    }

    object Extras {
        const val WF_STEP = "wfStep"
        const val SANDBOX_RECORD = "sandbox_record"
    }

    object VideoUploadStatus {
        const val NOT_UPLOADED = 0
        const val UPLOADED = 1
        const val DUPLICATE = 2
    }
}