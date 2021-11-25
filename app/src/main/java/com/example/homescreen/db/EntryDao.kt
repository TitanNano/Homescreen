package com.example.homescreen.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EntryDao {

    @Query("SELECT identifier as id, COUNT(identifier) as usage FROM usage_entries WHERE used_at > :threshold GROUP BY identifier")
    fun getAllUsage(threshold: Long): List<AgregatedUsage>

    @Query("SELECT identifier as id, COUNT(id) as usage FROM usage_entries WHERE identifier = :id AND used_at > :threshold")
    fun getUsage(id: String, threshold: Long): AgregatedUsage?

    @Insert
    fun addUsage(entry: UsageEntry)

}