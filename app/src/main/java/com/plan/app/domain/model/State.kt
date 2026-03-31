package com.plan.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing a State (for regions).
 */
@Parcelize
data class State(
    val id: Long = 0,
    val name: String,
    val color: Int
) : Parcelable {
    companion object {
        // Pre-defined colors for states
        val PREDEFINED_COLORS = listOf(
            0xFFFF0000.toInt(), // Red
            0xFFFFA500.toInt(), // Orange
            0xFFFFFF00.toInt(), // Yellow
            0xFF00FF00.toInt(), // Green
            0xFF00FFFF.toInt(), // Cyan
            0xFF0000FF.toInt(), // Blue
            0xFF8B00FF.toInt()  // Violet
        )
        
        val PREDEFINED_STATE_NAMES = listOf(
            "Critical",    // Red
            "Warning",     // Orange
            "Attention",   // Yellow
            "Normal",      // Green
            "Info",        // Cyan
            "Complete",    // Blue
            "Other"        // Violet
        )
    }
}
