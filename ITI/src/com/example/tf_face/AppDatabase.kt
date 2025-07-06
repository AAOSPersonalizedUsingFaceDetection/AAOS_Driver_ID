package com.example.tf_face

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import android.util.Log
@TypeConverters(Converters::class)
@Database(entities = [FaceEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DB_PATH = "/data/system/tf_face/face_database.db"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = File(DB_PATH)

                // Ensure directory exists
                dbFile.parentFile?.mkdirs()
		// Ensure parent directory exists BEFORE Room tries to open the DB
		val parentDir = dbFile.parentFile
		if (parentDir != null && !parentDir.exists()) {
		    val success = parentDir.mkdirs()
		    if (!success) {
			Log.e("AppDatabase", "Failed to create DB directory: ${parentDir.absolutePath}")
		    }
		}
                // Build custom helper factory to override the DB location
                val factory = object : SupportSQLiteOpenHelper.Factory {
                    override fun create(config: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
                        val newConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context)
                            .name(dbFile.absolutePath)
                            .callback(config.callback)
                            .build()
                        return FrameworkSQLiteOpenHelperFactory().create(newConfig)
                    }
                }

                val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, dbFile.absolutePath)
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

