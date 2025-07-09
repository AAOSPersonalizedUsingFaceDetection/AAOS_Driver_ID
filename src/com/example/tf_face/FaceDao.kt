package com.example.tf_face

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

// FaceDao.kt
@Dao
interface FaceDao {
    @Insert
    suspend fun insert(face: FaceEntity)

    @Query("SELECT * FROM FaceEntity")
    suspend fun getAllFaces(): List<FaceEntity>

    @Query("SELECT * FROM FaceEntity WHERE name = :name")
    suspend fun getFacesByName(name: String): List<FaceEntity>

    @Delete
    suspend fun delete(face: FaceEntity)
    
    @Query("DELETE FROM FaceEntity WHERE name = :name")
    suspend fun deleteFacesByName(name: String)
}