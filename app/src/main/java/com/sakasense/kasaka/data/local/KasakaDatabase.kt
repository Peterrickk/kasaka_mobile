package com.sakasense.kasaka.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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
            Room.databaseBuilder(context, KasakaDatabase::class.java, "kasaka.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: temperature and humidity on readings (firmware sends `temp` / `hum`). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE readings ADD COLUMN temperatureC REAL")
                db.execSQL("ALTER TABLE readings ADD COLUMN humidityPct REAL")
            }
        }

        /** v3: Kasaka Guidebook codes 1–7 are per crop, so recommendations remember their crop. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recommendations ADD COLUMN crop TEXT")
            }
        }
    }
}
