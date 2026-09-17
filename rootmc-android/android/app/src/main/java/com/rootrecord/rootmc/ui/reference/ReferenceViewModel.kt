package com.rootrecord.rootmc.ui.reference

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.ReferenceCacheEntity
import com.rootrecord.rootmc.data.repository.ReferenceRepository
import com.rootrecord.rootmc.util.ReferenceDisplayItem
import com.rootrecord.rootmc.util.ReferenceItemParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReferenceUiState(
    val categories: List<String> = emptyList(),
    val entries: List<ReferenceCacheEntity> = emptyList(),
    val category: String? = null,
    val searchQuery: String = "",
)

@HiltViewModel
class ReferenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val referenceRepository: ReferenceRepository,
) : ViewModel() {

    private val initialCategory: String? = savedStateHandle.get<String>("category")
    private val selectedCategory = MutableStateFlow(initialCategory)
    private val searchQuery = MutableStateFlow("")

    private val categoryEntries: StateFlow<List<ReferenceCacheEntity>> = selectedCategory
        .flatMapLatest { category ->
            if (category.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                referenceRepository.observeByCategory(category)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = referenceRepository.observeCategories()
        .map { list -> list.filter { it != "meta" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ReferenceUiState> = combine(
        categories,
        selectedCategory,
        searchQuery,
        categoryEntries,
    ) { cats, category, query, entries ->
        ReferenceUiState(
            categories = cats,
            category = category,
            entries = entries,
            searchQuery = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReferenceUiState())

    val displayItems: StateFlow<List<ReferenceDisplayItem>> = combine(
        categoryEntries,
        searchQuery,
    ) { entries, query ->
        filterDisplayItems(entries, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            referenceRepository.syncFromRemote()
        }
        initialCategory?.let { selectedCategory.value = it }
    }

    fun loadCategory(category: String) {
        selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun refreshRemote() {
        viewModelScope.launch {
            referenceRepository.syncFromRemote()
        }
    }

    private companion object {
        fun filterDisplayItems(
            entries: List<ReferenceCacheEntity>,
            searchQuery: String,
        ): List<ReferenceDisplayItem> {
            val q = searchQuery.trim().lowercase()
            val items = entries
                .flatMap { ReferenceItemParser.parseBundle(it.jsonBlob) }
                .distinctBy { it.id }
            if (q.isEmpty()) return items
            return items.filter {
                it.id.lowercase().contains(q) ||
                    it.title.lowercase().contains(q) ||
                    it.subtitle.lowercase().contains(q)
            }
        }
    }
}
