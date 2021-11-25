package com.example.homescreen.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.matrix.roomigrant.GenerateRoomMigrations

@Database(entities = [UsageEntry::class, IconConfig::class], version = 2)
@GenerateRoomMigrations
abstract class UsageDatabase: RoomDatabase() {

    abstract fun entryDao(): EntryDao
    abstract fun iconConfigDao(): IconConfigDao

    companion object {
        @Volatile private var instance: UsageDatabase? = null

        @Synchronized
        fun get(context: Context): UsageDatabase {
            if (this.instance == null) {
                this.instance = Room.databaseBuilder(
                    context,
                    UsageDatabase::class.java,
                    "entry-usage-data"
                )
                    .addMigrations(*UsageDatabase_Migrations.build())
                    .build()
            }

            return this.instance!!
        }
    }
}