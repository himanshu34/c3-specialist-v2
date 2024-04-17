package co.nayan.c3specialist_v2.screen_sharing.config

object MeetingServiceConstants {
    const val START_SERVICE = "start_service"
    const val SCREEN_RESOLUTION_SCALE = 2
    const val STOP_SCREEN_SHARING = "cancel_stream"
    const val RETURN = "return"
    const val START_SCREEN_SHARING = "start_stream"
    const val REMOTE_CONNECTED = "remote_connected"
    const val REMOTE_DISCONNECTED = "remote_disconnected"
    const val MEDIA_PROJECTION_REQUEST = "media_projection_request"
    const val SETUP_SCREEN_SHARING_UI_FOR_REMOTE = "setup_screen_sharing_ui_for_remote"
    const val CHECK_PERMISSIONS = "check_permissions"
    const val CALL_END = "call_end"
}

object MeetingInvitationAction {
    const val IN_COMING_CALL = "in_coming_call"
    const val RECEIVE = "receive"
    const val CANCEL = "cancel"
}

object MeetingIntent {
    const val OBSERVERS_REMOVED = "observers_removed"
    const val HUNG_UP = "hung_up"
    const val RESUME_AUDIO = "resume_audio"
    const val PAUSE_AUDIO = "pause_audio"
    const val START_SCREEN_SHARING = "start_screen_sharing"
    const val STOP_SCREEN_SHARING = "stop_screen_sharing"
    const val START_SCREEN_CAPTURE = "start_screen_capture"
    const val PERMISSIONS_GRANTED = "permissions_granted"
}

object UserStatus {
    const val CONNECTING = "Connecting..."
    const val CONNECTED = "Connected"
    const val DISCONNECTED = "Disconnected"
}