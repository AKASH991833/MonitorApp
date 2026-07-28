package com.familyconnect.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paired_children")
data class PairedChildEntity(
    @PrimaryKey val childId: String,
    val childName: String,
    val fcmToken: String,
    val isOnline: Boolean,
    val lastSeen: Long,
    val pairedAt: Long
)
