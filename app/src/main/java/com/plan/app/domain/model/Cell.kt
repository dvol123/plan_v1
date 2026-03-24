package com.plan.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing a Cell in the grid.
 */
@Parcelize
data class Cell(
    val row: Int,
    val col: Int
) : Parcelable {
    fun isAdjacentTo(other: Cell): Boolean {
        val rowDiff = kotlin.math.abs(row - other.row)
        val colDiff = kotlin.math.abs(col - other.col)
        return rowDiff <= 1 && colDiff <= 1 && !(rowDiff == 0 && colDiff == 0)
    }
}
