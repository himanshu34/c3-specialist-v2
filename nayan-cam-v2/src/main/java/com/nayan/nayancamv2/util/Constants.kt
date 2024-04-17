package com.nayan.nayancamv2.util

object Constants {
    const val OPTICAL_FLOW_THRESHOLD = 45
    const val CONSECUTIVE_RECORDING_DELAY = 6000L
    const val SAVING_VIDEO_DELAY = 5000L
    const val DIRECTORY_NAME = "NAYAN"
    const val CIRCULAR_ENCODER_FPS = 30
    const val CIRCULAR_ENCODER_BUFFER_LENGTH_IN_SEC = 11
    const val DATE_FORMAT = "MM-dd-yy"
    const val TIME_FORMAT = "HH-mm-ss"
    const val VIDEO_UPLOADER_STATUS = "com.nayan.VideoUploader.VIDEO_UPLOADER_STATUS"
    const val VIDEO_UPLOADER_TAG = "GBM_VIDEO_UPLOADER"
    const val SEGMENTS_SYNC_TAG = "SEGMENTS_SYNC_TAG"
    const val ATTENDANCE_SYNC_TAG = "ATTENDANCE_SYNC_TAG"
    const val AI_MODELS_SYNC_TAG = "AI_MODELS_SYNC_TAG"
    const val VIDEO_FILES_SYNC_TAG = "VIDEO_FILES_SYNC_TAG"
    const val ALLOCATED_PHONE_STORAGE_KEY = "ALLOCATED_PHONE_STORAGE_KEY"
    const val WIFI_DATA = 1
    const val MOBILE_DATA = 2
    const val UPLOAD_NETWORK_TYPE = "upload_network_type"
    const val KEY_DEFAULT_HOVER_MODE = "KEY_DEFAULT_HOVER_MODE"
    const val KEY_LAST_TIME_IMAGE_AVAILABLE_CALLED = "KEY_LAST_TIME_IMAGE_AVAILABLE_CALLED"
    const val ACTION_OPEN_DASHBOARD = "com.nayan.ACTION_OPEN_DASHBOARD"
    const val ACTION_STOP_CAMERA_SERVICE = "com.nayan.ACTION_STOP_CAMERA_SERVICE"
    const val IS_FROM_BACKGROUND = "com.nayan.IS_FROM_BACKGROUND"
    const val IS_FROM_HOVER = "IS_FROM_HOVER"
    const val SHOULD_HOVER = "SHOULD_HOVER"
    const val ACTION_EXIT = "com.nayan.ACTION_EXIT"

    const val ACTION_OPEN_RECORDER = "com.nayan.ACTION_OPEN_RECORDER"
    const val ACTION_OPTIMIZE_FRAMES = "com.nayan.OPTIMIZE_FRAMES"
    const val LAST_RESTART = "LAST_RESTART"
    const val VOLUME_LEVEL = "VOLUME_LEVEL"
    const val KEY_DEFAULT_LITE_MODE = "KEY_DEFAULT_LITE_MODE"
    const val KEY_FORCED_LITE_MODE = "KEY_FORCED_LITE_MODE"
    const val SHOW_AI_PREVIEW = "SHOW_AI_PREVIEW"
    const val RADIAN_TO_METER_DIVISOR_EARTH = 111111
    const val MAX_CONCURRENT_PROCESSING = 2
    const val LOCATION_ACCURACY_THRESHOLD = 16.0 // Accept only locations with accuracy better than 16 meters
}

object DEVICE_PERFORMANCE {
    const val SURVEYOR_SPEED_MAX_THRESHOLD =
        5.4 // returns km/h as per second (40kph = 11.111..m/s)
    const val HOVER_RESTART_THRESHOLD = 15
    const val HOVER_RESTART_THRESHOLD_KENT = 60
    const val LAST_CAMERA_FRAME_THRESHOLD = 15
    const val DELAYED_2_sec = (2 * 1000).toLong()
    const val DELAYED_1 = (1 * 60 * 1000).toLong()
    const val DELAYED_5 = (5 * 60 * 1000).toLong()
    const val DELAYED_10 = (10 * 60 * 1000).toLong()
    const val DELAYED_15 = (15 * 60 * 1000).toLong()
    const val DELAYED_ONE_WEEK = (7 * 24 * 60 * 60 * 1000).toLong()
    const val CPU = "CPU"
    const val BATTERY = "Battery"
}

object RecordingEventState {
    const val ORIENTATION_ERROR = 0
    const val RECORDING_STARTED = 1
    const val RECORDING_SUCCESSFUL = 2
    const val RECORDING_FAILED = 3
    const val RECORDING_CORRUPTED = 4
    const val AI_SCANNING = 5
    const val DRIVING_FAST = 6
    const val NOT_IN_SURGE = 7
    const val AI_CONSECUTIVE = 8
}

object Notifications {
    const val NOTIFICATION_TYPE = "notification_type"
    const val EVENT_TYPE = "event_type"
    const val EVENTS_PAYOUT = "Events Payout"
    const val VIDEO_PAYOUT = "Video Payout"
    const val POINTS_RECEIVED = "points_received"
    const val AMOUNT_RECEIVED = "amount_received"
}

object SamplingRate {
    const val SAMPLING_RATE = 1
    const val SAMPLING_RATE_LITE = SAMPLING_RATE * 3
}

const val PHONE_EXTERNAL_STORAGE = "PHONE_EXTERNAL_STORAGE"
const val SD_CARD_STORAGE = "SD_CARD_STORAGE"

const val BROADCAST_NOTIFICATION = "BROADCAST_NOTIFICATION"
