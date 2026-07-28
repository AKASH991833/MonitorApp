package com.familyconnect.app.data.model

data class SessionRecord(
    val sessionId: String = "",
    val childId: String = "",
    val parentId: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val duration: Long? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val status: String = ""
)
