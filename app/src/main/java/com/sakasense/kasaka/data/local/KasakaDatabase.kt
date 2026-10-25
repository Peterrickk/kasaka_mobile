package com.sakasense.kasaka.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun rankedCropsToJson(value: List<RankedCrop>): String = Json.encodeToString(value)

    @TypeConverter
    fun rankedCropsFromJson(value: String): List<RankedCrop> = Json.decodeFromString(value)
}

@Database(
    entities = [
        FarmEntity::class, PlotEntity::class, DeviceEntity::class,
        ReadingEntity::class, RecommendationEntity::class, AlertEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KasakaDatabase : RoomDatabase() {
    abstract fun farms(): FarmDao
    abstract fun plots(): PlotDao
    abstract fun devices(): DeviceDao
    abstract fun readings(): ReadingDao
    abstract fun recommendations(): RecommendationDao
    abstract fun alerts(): AlertDao

    companion object {
        fun create(context: Context): KasakaDatabase =
            Room.databaseBuilder(context, KasakaDatabase::class.java, "kasaka.db").build()
    }
}
