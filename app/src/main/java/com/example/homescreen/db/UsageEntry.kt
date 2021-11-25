package com.example.homescreen.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_entries")
data class UsageEntry (
    @ColumnInfo(name = "identifier")
    val identifier: String,

    @ColumnInfo(name = "used_at")
    val usedAt: Long = System.currentTimeMillis(),

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)