package com.example.tf_face

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DatabaseInitializer(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    suspend fun initializeDatabaseIfNeeded() = withContext(Dispatchers.IO) {
        val isInitialized = sharedPreferences.getBoolean("isDatabaseInitialized", true)
        if (!isInitialized) {
            //clearDatabase()
            sharedPreferences.edit().putBoolean("isDatabaseInitialized", true).apply()
            Log.d("DatabaseInitializer", "Database initialized (cleared)")
        } else {
            Log.d("DatabaseInitializer", "Database already initialized")
        }
    }

//     @Query("DELETE FROM FaceEntity WHERE name = :name")
// suspend fun deleteFacesByName(name: String)

    suspend fun deleteFacesByName(name: String) = withContext(Dispatchers.IO) {
        try {
            database.faceDao().deleteFacesByName(name)
            Log.d("DatabaseInitializer", "Deleted faces for $name")
            return@withContext true
        } catch (e: Exception) {
            Log.e("DatabaseInitializer", "Error deleting faces for $name", e)
            return@withContext false
        }
    }
    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        database.faceDao().getAllFaces().forEach {
            database.faceDao().delete(it)
        }
        Log.d("DatabaseInitializer", "Database cleared")
    }

    // DatabaseInitializer.kt
    suspend fun addFace(name: String, imageBitmap: Bitmap, imageName: String, theme: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Save bitmap as JPEG to internal storage
            val imageDir = File("/data/system/tf_face/images")
		imageDir.mkdirs()
		val file = File(imageDir, imageName)
		FileOutputStream(file).use { out ->
		    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
		}
		Log.d("DatabaseInitializer", "Saved image to ${file.absolutePath}")
            // Detect and crop face
            val detector = BlazeFaceDetector(context)
            val faces = detector.detect(imageBitmap)
            val largestFace = faces.maxByOrNull { it.width() * it.height() } ?: return@withContext false
            val faceBitmap = detector.cropFace(imageBitmap, largestFace)
            val embedding = detector.getFaceEmbedding(faceBitmap)

            // Insert into database with theme
            database.faceDao().insert(FaceEntity(
                name = name,
                embedding = embedding,
                imageUri = imageName,
                theme = theme
            ))
            Log.d("DatabaseInitializer", "Inserted face for $name with image $imageName and theme $theme")
            return@withContext true
        } catch (e: Exception) {
            Log.e("DatabaseInitializer", "Error adding face for $name with image $imageName", e)
            return@withContext false
        }
    }

    suspend fun getFrameCountForUser(name: String): Int = withContext(Dispatchers.IO) {
        database.faceDao().getFacesByName(name).size
    }
    // In DatabaseInitializer.kt
    suspend fun getThemeForUser(name: String): String? = withContext(Dispatchers.IO) {
        database.faceDao().getFacesByName(name).firstOrNull()?.theme
    }
}
