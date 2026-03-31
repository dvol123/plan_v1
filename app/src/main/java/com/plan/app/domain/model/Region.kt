package com.plan.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing a Region (area/button on a project).
 */
@Parcelize
data class Region(
    val id: Long = 0,
    val projectId: Long,
    val name: String,
    val stateId: Long? = null,
    val state: State? = null,
    val type1: String? = null,
    val type2: String? = null,
    val description: String? = null,
    val note: String? = null,
    val cells: List<Cell> = emptyList(),
    val contents: List<Content> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable
