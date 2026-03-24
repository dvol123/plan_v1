package com.plan.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a State in the database.
 * States have a name and color, used to categorize regions.
 * Pre-filled with 7 classic colors: red, orange, yellow, green, cyan, blue, violet.
 */
@Entity(tableName = "states")
data class StateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int
)
