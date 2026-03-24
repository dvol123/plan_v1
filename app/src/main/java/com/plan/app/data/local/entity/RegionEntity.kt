package com.plan.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a Region (area/button) in the database.
 * A region belongs to a project and contains cells that define its shape.
 */
@Entity(
    tableName = "regions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StateEntity::class,
            parentColumns = ["id"],
            childColumns = ["stateId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("projectId"), Index("stateId")]
)
data class RegionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val name: String,
    val stateId: Long? = null,
    val type1: String? = null,
    val type2: String? = null,
    val description: String? = null,
    val note: String? = null,
    val cellsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
