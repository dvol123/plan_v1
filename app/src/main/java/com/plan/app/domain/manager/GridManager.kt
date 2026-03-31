package com.plan.app.domain.manager

import com.plan.app.domain.model.Cell
import com.plan.app.domain.model.Region
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for grid editing state.
 */
@Singleton
class GridManager @Inject constructor() {
    
    // Selected cells during editing
    private val _selectedCells = MutableStateFlow<Set<Cell>>(emptySet())
    val selectedCells: StateFlow<Set<Cell>> = _selectedCells.asStateFlow()
    
    // Grid cell size
    private val _cellSize = MutableStateFlow(1)
    val cellSize: StateFlow<Int> = _cellSize.asStateFlow()
    
    // Whether we're in editing mode
    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()
    
    // Currently selected region for editing
    private val _editingRegion = MutableStateFlow<Region?>(null)
    val editingRegion: StateFlow<Region?> = _editingRegion.asStateFlow()
    
    // Grid dimensions
    private val _gridWidth = MutableStateFlow(0)
    val gridWidth: StateFlow<Int> = _gridWidth.asStateFlow()
    
    private val _gridHeight = MutableStateFlow(0)
    val gridHeight: StateFlow<Int> = _gridHeight.asStateFlow()
    
    fun setEditingMode(isEditing: Boolean) {
        _isEditing.value = isEditing
        if (!isEditing) {
            clearSelection()
        }
    }
    
    fun setCellSize(size: Int) {
        _cellSize.value = size
    }
    
    fun setGridDimensions(width: Int, height: Int) {
        _gridWidth.value = width
        _gridHeight.value = height
    }
    
    fun toggleCellSelection(cell: Cell): Boolean {
        val currentSelection = _selectedCells.value.toMutableSet()
        
        return if (currentSelection.contains(cell)) {
            // Can only remove the last added cell
            val lastCell = currentSelection.lastOrNull()
            if (lastCell == cell) {
                currentSelection.remove(cell)
                _selectedCells.value = currentSelection
                true
            } else {
                false
            }
        } else {
            // Can only add adjacent cells
            if (currentSelection.isEmpty() || currentSelection.any { it.isAdjacentTo(cell) }) {
                currentSelection.add(cell)
                _selectedCells.value = currentSelection
                true
            } else {
                false
            }
        }
    }
    
    fun startNewSelection(cell: Cell) {
        _selectedCells.value = setOf(cell)
    }
    
    fun clearSelection() {
        _selectedCells.value = emptySet()
        _editingRegion.value = null
    }
    
    fun setEditingRegion(region: Region?) {
        _editingRegion.value = region
    }
    
    fun hasSelection(): Boolean = _selectedCells.value.isNotEmpty()
    
    fun getSelectedCellsList(): List<Cell> = _selectedCells.value.toList()
}
