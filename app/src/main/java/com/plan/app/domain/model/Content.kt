package com.plan.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing Content attached to a region.
 */
@Parcelize
data class Content(
    val id: Long = 0,
    val regionId: Long,
    val type: ContentType,
    val data: String,
    val originalFileName: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable

enum class ContentType {
    TEXT, PHOTO, VIDEO, FILE
}
