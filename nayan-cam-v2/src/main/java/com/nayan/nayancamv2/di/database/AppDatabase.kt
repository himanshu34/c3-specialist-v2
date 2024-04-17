package com.nayan.nayancamv2.di.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import co.nayan.c3v2.core.debugMode
import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData
import javax.inject.Singleton

private const val DATABASE_NAME = "C3_NAYAN_LOCATOR.db"

@Singleton
@Database(
    entities = [LocationData::class, SegmentTrackData::class, VideoUploaderData::class],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun getLocationHistoryDAO(): LocationDAO
    abstract fun getSegmentTrackingDAO(): SegmentTrackDAO
    abstract fun getVideoUploaderDAO(): VideoUploaderDAO

    companion object {
        @Volatile
        var instance: AppDatabase? = null
        private val LOCK = Any()
        operator fun invoke(context: Context) = instance ?: synchronized(LOCK) {
            instance ?: buildTVDatabase(context).also {
                instance = it
            }
        }

        private fun buildTVDatabase(context: Context) = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME
        ).also {
            it.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            debugMode { it.allowMainThreadQueries() }
        }.build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE SegmentTracking ADD COLUMN last_updated INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `SegmentTrackingNew`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `SegmentTrackingNew` (`segment_coordinates` TEXT NOT NULL, `count` INTEGER NOT NULL DEFAULT 1, `last_updated` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`segment_coordinates`))")
                db.execSQL("INSERT INTO `SegmentTrackingNew` (segment_coordinates, count, last_updated) SELECT segment_coordinates, count, last_updated FROM `SegmentTracking`")
                db.execSQL("DROP TABLE IF EXISTS `SegmentTracking`")
                db.execSQL("ALTER TABLE `SegmentTrackingNew` RENAME TO `SegmentTracking`")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migrate LocationHistory table
                db.execSQL("DROP TABLE IF EXISTS `LocationHistoryNew`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `LocationHistoryNew` (`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `time_stamp` INTEGER NOT NULL, PRIMARY KEY(`time_stamp`))")
                db.execSQL("INSERT INTO `LocationHistoryNew` (latitude, longitude, time_stamp) SELECT latitude, longitude, time_stamp FROM `LocationHistory`")
                db.execSQL("DROP TABLE IF EXISTS `LocationHistory`")
                db.execSQL("ALTER TABLE `LocationHistoryNew` RENAME TO `LocationHistory`")

                // Migrate SegmentTracking table
                db.execSQL("DROP TABLE IF EXISTS `SegmentTrackingNew`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `SegmentTrackingNew` (`segment_coordinates` TEXT NOT NULL, `count` INTEGER NOT NULL DEFAULT 1, `last_updated` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`segment_coordinates`))")
                db.execSQL("INSERT INTO `SegmentTrackingNew` (segment_coordinates, count, last_updated) SELECT segment_coordinates, count, last_updated FROM `SegmentTracking`")
                db.execSQL("DROP TABLE IF EXISTS `SegmentTracking`")
                db.execSQL("ALTER TABLE `SegmentTrackingNew` RENAME TO `SegmentTracking`")

                // Migrate VideoUploader table
                db.execSQL("CREATE TABLE IF NOT EXISTS `VideoUploader` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `video_name` TEXT NOT NULL, `local_video_file_path` TEXT NOT NULL, `video_id` INTEGER NOT NULL, `upload_status` INTEGER NOT NULL, `created_at_timestamp` INTEGER NOT NULL, `uploaded_at_timestamp` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE LocationHistory ADD COLUMN accuracy REAL NOT NULL DEFAULT 0.0")
            }
        }
    }
}