package com.sakasense.kasaka.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmDao {
    @Query("SELECT * FROM farms ORDER BY createdAt")
    fun observeAll(): Flow<List<FarmEntity>>

    @Query("SELECT * FROM farms WHERE id = :id")
    suspend fun get(id: String): FarmEntity?

    @Upsert
    suspend fun upsert(vararg farm: FarmEntity)

    @Query("DELETE FROM farms WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PlotDao {
    @Query("SELECT * FROM plots ORDER BY farmId, layoutRow, layoutCol")
    fun observeAll(): Flow<List<PlotEntity>>

    @Query("SELECT * FROM plots WHERE id = :id")
    fun observe(id: String): Flow<PlotEntity?>

    @Query("SELECT * FROM plots WHERE id = :id")
    suspend fun get(id: String): PlotEntity?

    @Query("SELECT * FROM plots WHERE farmId = :farmId")
    suspend fun forFarm(farmId: String): List<PlotEntity>

    @Upsert
    suspend fun upsert(vararg plot: PlotEntity)

    @Query("UPDATE plots SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: PlotStatus)

    @Query("DELETE FROM plots WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY serialNumber")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    suspend fun all(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE serialNumber = :serial")
    suspend fun bySerial(serial: String): DeviceEntity?

    @Upsert
    suspend fun upsert(vararg device: DeviceEntity)
}

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings WHERE plotId = :plotId AND timestamp >= :since ORDER BY timestamp")
    fun observeForPlot(plotId: String, since: Long): Flow<List<ReadingEntity>>

    /** Newest reading of every plot. */
    @Query(
        """SELECT r.* FROM readings r
           JOIN (SELECT plotId, MAX(timestamp) AS ts FROM readings GROUP BY plotId) m
             ON r.plotId = m.plotId AND r.timestamp = m.ts"""
    )
    fun observeLatestPerPlot(): Flow<List<ReadingEntity>>

    @Query(
        """SELECT r.* FROM readings r
           JOIN (SELECT plotId, MAX(timestamp) AS ts FROM readings GROUP BY plotId) m
             ON r.plotId = m.plotId AND r.timestamp = m.ts"""
    )
    suspend fun allLatest(): List<ReadingEntity>

    @Query("SELECT * FROM readings WHERE plotId = :plotId ORDER BY timestamp DESC")
    suspend fun allForPlot(plotId: String): List<ReadingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(readings: List<ReadingEntity>)
}

@Dao
interface RecommendationDao {
    @Query("SELECT * FROM recommendations WHERE plotId = :plotId ORDER BY timestamp DESC")
    fun observeForPlot(plotId: String): Flow<List<RecommendationEntity>>

    @Query(
        """SELECT r.* FROM recommendations r
           JOIN (SELECT plotId, MAX(timestamp) AS ts FROM recommendations GROUP BY plotId) m
             ON r.plotId = m.plotId AND r.timestamp = m.ts"""
    )
    fun observeLatestPerPlot(): Flow<List<RecommendationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recs: List<RecommendationEntity>)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY triggeredAt DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE resolvedAt IS NULL")
    suspend fun open(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alerts: List<AlertEntity>)

    @Query("UPDATE alerts SET resolvedAt = :at WHERE id = :id")
    suspend fun resolve(id: String, at: Long)

    @Query("UPDATE alerts SET resolvedAt = :at WHERE plotId = :plotId AND type = :type AND resolvedAt IS NULL")
    suspend fun resolveForPlot(plotId: String, type: AlertType, at: Long)
}
