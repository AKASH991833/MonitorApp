package com.familyconnect.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val childId: String,
    val parentId: String,
    val startTime: Long,
    val endTime: Long?,
    val duration: Long?,
    val locationLat: Double?,
    val locationLng: Double?,
    val status: String
)
