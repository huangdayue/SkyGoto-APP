package com.skygoto.app.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.domain.model.CelestialObject
import com.skygoto.app.domain.model.ObjectType
import com.skygoto.app.domain.repository.CatalogRepository
import com.skygoto.app.domain.repository.CatalogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val objects: List<CelestialObject> = emptyList(),
    val filteredObjects: List<CelestialObject> = emptyList(),
    val searchQuery: String = "",
    val selectedCatalog: CatalogType = CatalogType.ALL,
    val selectedType: ObjectType? = null,
    val selectedObject: CelestialObject? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()
    
    init {
        loadCatalogs()
    }
    
    private fun loadCatalogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val all = catalogRepository.getMessierCatalog() + catalogRepository.getNGCCatalog()
            
            _uiState.update { state ->
                state.copy(
                    objects = all,
                    filteredObjects = all,
                    isLoading = false
                )
            }
        }
    }
    
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }
    
    fun selectCatalog(catalog: CatalogType) {
        _uiState.update { it.copy(selectedCatalog = catalog) }
        applyFilters()
    }
    
    fun filterByType(type: ObjectType?) {
        _uiState.update { it.copy(selectedType = type) }
        applyFilters()
    }
    
    fun selectObject(obj: CelestialObject?) {
        _uiState.update { it.copy(selectedObject = obj) }
    }
    
    private fun applyFilters() {
        val state = _uiState.value
        var filtered = state.objects
        
        // 按目录筛选
        filtered = when (state.selectedCatalog) {
            CatalogType.MESSIER -> filtered.filter { it.id.startsWith("M") }
            CatalogType.NGC -> filtered.filter { it.id.startsWith("NGC") }
            CatalogType.IC -> filtered.filter { it.id.startsWith("IC") }
            CatalogType.ALL -> filtered
        }
        
        // 按类型筛选
        state.selectedType?.let { type ->
            filtered = filtered.filter { it.type == type }
        }
        
        // 按搜索词筛选
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            filtered = filtered.filter { obj ->
                obj.id.lowercase().contains(q) ||
                obj.name.lowercase().contains(q) ||
                obj.constellation.lowercase().contains(q) ||
                obj.altNames.any { it.lowercase().contains(q) }
            }
        }
        
        _uiState.update { it.copy(filteredObjects = filtered) }
    }
    
    fun getObjectForGoto(): Pair<String, String>? {
        val obj = _uiState.value.selectedObject ?: return null
        return Pair(obj.ra, obj.dec)
    }
}