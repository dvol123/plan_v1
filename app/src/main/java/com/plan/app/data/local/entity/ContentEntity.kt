package com.plan.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing content attached to a region.
 * Types: "text", "photo", "video", "file"
 * For text, data contains the text content.
 * For photo/video/file, data contains the relative path to the file.
 */
@Entity(
    tableName = "contents",
    foreignKeys = [
        ForeignKey(
            entity = RegionEntity::class,
            parentColumns = ["id"],
            childColumns = ["regionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("regionId")]
)
data class ContentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val regionId: Long,
    val type: ContentType,
    val data: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ContentType {
    TEXT, PHOTO, VIDEO, FILE
}
