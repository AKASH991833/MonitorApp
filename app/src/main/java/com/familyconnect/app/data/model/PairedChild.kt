package com.familyconnect.app.data.model

data class PairedChild(
    val childId: String = "",
    val childName: String = "",
    val fcmToken: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val pairedAt: Long = 0L
)
