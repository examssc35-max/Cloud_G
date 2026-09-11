package com.example.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.ThemeSetting
import com.example.domain.model.Album
import com.example.domain.model.GallerySortOrder
import com.example.domain.model.GalleryTab
import com.example.domain.model.MediaItem
import com.example.domain.model.R2ConnectionStatus
import com.example.domain.repository.CloudflareRepository
import com.example.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GalleryUiState(
    val selectedTab: GalleryTab = GalleryTab.ALL,
    val selectedAlbum: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasPermission: Boolean = true
)

class GalleryViewModel(
    private val galleryRepository: GalleryRepository,
    private val cloudflareRepository: CloudflareRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    val gridColumns: StateFlow<Int> = preferencesManager.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val sortOrder: StateFlow<GallerySortOrder> = preferencesManager.sortOrderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GallerySortOrder.DATE_DESC)

    val albums: StateFlow<List<Album>> = galleryRepository.albumsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val r2Status: StateFlow<R2ConnectionStatus> = cloudflareRepository.connectionStatus

    private val _cloudMedia = MutableStateFlow<List<MediaItem>>(emptyList())

    val mediaItems: StateFlow<List<MediaItem>> = combine(
        galleryRepository.localMediaFlow,
        _cloudMedia,
        _uiState,
        sortOrder
    ) { localItems, cloudItems, state, sort ->
        var list = when (state.selectedTab) {
            GalleryTab.ALL -> localItems + cloudItems
            GalleryTab.PHOTOS -> (localItems + cloudItems).filter { !it.isVideo }
            GalleryTab.VIDEOS -> (localItems + cloudItems).filter { it.isVideo }
            GalleryTab.ALBUMS -> {
                if (state.selectedAlbum != null) {
                    (localItems + cloudItems).filter { it.albumName == state.selectedAlbum }
                } else {
                    localItems
                }
            }
            GalleryTab.CLOUD -> cloudItems
        }

        // Apply Album filter if any
        if (state.selectedAlbum != null && state.selectedTab != GalleryTab.ALBUMS) {
            list = list.filter { it.albumName == state.selectedAlbum }
        }

        // Apply Search query
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter { it.name.lowercase().contains(q) }
        }

        // Apply Sorting
        when (sort) {
            GallerySortOrder.DATE_DESC -> list.sortedByDescending { it.dateModified }
            GallerySortOrder.DATE_ASC -> list.sortedBy { it.dateModified }
            GallerySortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            GallerySortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            GallerySortOrder.SIZE_DESC -> list.sortedByDescending { it.size }
            GallerySortOrder.SIZE_ASC -> list.sortedBy { it.size }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically fetch top-level cloud images when connected
        viewModelScope.launch {
            cloudflareRepository.connectionStatus.collect { status ->
                if (status is R2ConnectionStatus.Connected) {
                    loadCloudMedia()
                } else {
                    _cloudMedia.value = emptyList()
                }
            }
        }
    }

    fun loadCloudMedia() {
        viewModelScope.launch {
            val result = cloudflareRepository.listFolder("")
            if (result.isSuccess) {
                val cloudObjects = result.getOrNull().orEmpty()
                val converted = cloudObjects.mapNotNull { obj ->
                    if (!obj.isDirectory) {
                        cloudflareRepository.getMediaItemForCloudObject(obj)
                    } else null
                }
                _cloudMedia.value = converted
            }
        }
    }

    fun selectTab(tab: GalleryTab) {
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            selectedAlbum = if (tab != GalleryTab.ALBUMS) null else _uiState.value.selectedAlbum
        )
    }

    fun selectAlbum(albumName: String?) {
        _uiState.value = _uiState.value.copy(selectedAlbum = albumName)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = if (!active) "" else _uiState.value.searchQuery
        )
    }

    fun setSortOrder(order: GallerySortOrder) {
        viewModelScope.launch {
            preferencesManager.setSortOrder(order)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            preferencesManager.setGridColumns(columns)
        }
    }

    fun toggleFavorite(mediaId: String) {
        viewModelScope.launch {
            galleryRepository.toggleFavorite(mediaId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            galleryRepository.refreshLocalMedia()
            if (cloudflareRepository.connectionStatus.value is R2ConnectionStatus.Connected) {
                loadCloudMedia()
            }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermission = granted)
        if (granted) {
            refresh()
        }
    }

    class Factory(
        private val galleryRepository: GalleryRepository,
        private val cloudflareRepository: CloudflareRepository,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GalleryViewModel(galleryRepository, cloudflareRepository, preferencesManager) as T
        }
    }
}
