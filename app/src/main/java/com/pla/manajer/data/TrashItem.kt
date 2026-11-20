package com.pla.manajer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash")
data class TrashItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val isDir: Boolean,
    val deletedAt: Long = System.currentTimeMillis()
)