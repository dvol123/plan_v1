package com.plan.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing a Project.
 */
@Parcelize
data class Project(
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
) : Parcelable
