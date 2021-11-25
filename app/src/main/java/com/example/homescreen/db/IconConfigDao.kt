package com.example.homescreen.db

import androidx.room.*

@Dao
interface IconConfigDao {

    @Query("SELECT * FROM icon_config WHERE identifier = :id LIMIT 1")
    fun getConfig(id: String): IconConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addConfig(entry: IconConfig)
}