package com.example.homescreen.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "icon_config")
data class IconConfig(
    @PrimaryKey
    @ColumnInfo(name = "identifier")
    val identifier: String,

    @ColumnInfo(name = "hidden")
    var hidden: Boolean = false
)
