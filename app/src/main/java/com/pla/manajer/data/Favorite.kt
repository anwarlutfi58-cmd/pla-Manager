package com.pla.manajer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val path: String,
    val name: String,
    val isDir: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)