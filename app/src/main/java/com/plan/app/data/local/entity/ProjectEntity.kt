package com.plan.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a Project in the database.
 * A project is a unit of content that includes a main photo,
 * a set of areas (buttons), and attached media files and texts.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val photoUri: String,
    val type1: String? = null,
    val type2: String? = null,
    val description: String? = null,
    val note: String? = null,
    val cellSize: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
