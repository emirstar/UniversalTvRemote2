package com.batin.tvremote.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TvDeviceEntity::class],
    version = 1,
    exportSchema = true
)
abstract class TvRemoteDatabase : RoomDatabase() {
    abstract fun tvDeviceDao(): TvDeviceDao

    companion object {
        const val DATABASE_NAME = "tv_remote_devices.db"
    }
}
