package com.pla.manajer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun all(): Flow<List<TrashItem>>

    @Insert
    suspend fun add(t: TrashItem): Long

    @Delete
    suspend fun remove(t: TrashItem)
}