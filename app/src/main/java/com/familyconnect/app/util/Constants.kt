package com.familyconnect.app.util

object Constants {
    const val FCM_TOPIC_PREFIX = "family_connect_"

    const val STUN_SERVER = "stun:stun.l.google.com:19302"
    const val TURN_SERVER_URL = ""
    const val TURN_USERNAME = ""
    const val TURN_CREDENTIAL = ""

    const val PAIRING_CODE_TTL_MINUTES = 10L
    const val SESSION_AUTO_TIMEOUT_MINUTES = 15L
    const val DEFAULT_STREAM_QUALITY = "SD"

    const val DATABASE_NAME = "family_connect_db"
    const val SHARED_PREFS_NAME = "family_connect_prefs"

    const val KEY_ROLE = "pref_role"
    const val KEY_PARENT_NAME = "pref_parent_name"
    const val KEY_CHILD_NAME = "pref_child_name"
    const val KEY_THEME_MODE = "pref_theme_mode"
    const val KEY_AUTO_START = "pref_auto_start"
    const val KEY_STREAM_QUALITY = "pref_stream_quality"
    const val KEY_AUTO_TIMEOUT = "pref_auto_timeout"
    const val KEY_LOCATION_ENABLED = "pref_location_enabled"

    const val NOTIFICATION_CHANNEL_ID = "family_connect_stream"

    const val SESSION_ID_KEY = "session_id"
    const val IS_STREAMING_KEY = "is_streaming"

    // SOS
    const val SOS_ALERTS_REF = "sos_alerts"
    const val KEY_SOS_ENABLED = "pref_sos_enabled"

    // App usage
    const val KEY_APP_USAGE_ENABLED = "pref_app_usage_enabled"

    // Ambient audio
    const val KEY_AMBIENT_AUDIO = "pref_ambient_audio"

    // Status update interval (ms)
    const val STATUS_UPDATE_INTERVAL_MS = 30_000L
    const val LOCATION_UPDATE_INTERVAL_MS = 15_000L
}
