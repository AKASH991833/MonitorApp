package com.familyconnect.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE childId = :childId ORDER BY startTime DESC")
    suspend fun getSessionsByChildId(childId: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE childId = :childId ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(childId: String): SessionEntity?
}
