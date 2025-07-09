// FaceEntity.kt
package com.example.tf_face

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val embedding: FloatArray,
    val imageUri: String,
    val theme: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (imageUri != other.imageUri) return false
        if (theme != other.theme) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + imageUri.hashCode()
        result = 31 * result + theme.hashCode()
        return result
    }
}