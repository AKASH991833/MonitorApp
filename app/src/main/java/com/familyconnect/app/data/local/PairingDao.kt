package com.familyconnect.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PairingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pairedChild: PairedChildEntity)

    @Delete
    suspend fun delete(pairedChild: PairedChildEntity)

    @Query("SELECT * FROM paired_children")
    suspend fun getAll(): List<PairedChildEntity>

    @Query("SELECT * FROM paired_children WHERE childId = :childId LIMIT 1")
    suspend fun getById(childId: String): PairedChildEntity?

    @Query("SELECT * FROM paired_children WHERE childId = :childId LIMIT 1")
    suspend fun getByChildId(childId: String): PairedChildEntity?
}
