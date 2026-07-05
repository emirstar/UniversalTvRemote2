package com.batin.tvremote.data.local.db

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TvDeviceDao {

    @Query("SELECT * FROM tv_devices ORDER BY lastConnectedAtMillis DESC")
    fun observeAll(): Flow<List<TvDeviceEntity>>

    @Query("SELECT * FROM tv_devices WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TvDeviceEntity?

    @Query("SELECT * FROM tv_devices WHERE autoConnect = 1 ORDER BY lastConnectedAtMillis DESC LIMIT 1")
    suspend fun findAutoConnectCandidate(): TvDeviceEntity?

    @Upsert(onConflictStrategy = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: TvDeviceEntity)

    @Query("UPDATE tv_devices SET autoConnect = 0")
    suspend fun clearAutoConnectFlagOnAll()

    @Query("UPDATE tv_devices SET autoConnect = 1, lastConnectedAtMillis = :now WHERE id = :id")
    suspend fun markAsLastUsed(id: String, now: Long)

    @Query("DELETE FROM tv_devices WHERE id = :id")
    suspend fun delete(id: String)
}
