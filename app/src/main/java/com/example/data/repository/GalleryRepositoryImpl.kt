package com.example.data.repository

import com.example.data.local.MediaStoreDataSource
import com.example.data.local.PreferencesManager
import com.example.data.local.db.FavoriteDao
import com.example.data.local.db.FavoriteEntity
import com.example.domain.model.Album
import com.example.domain.model.MediaItem
import com.example.domain.repository.GalleryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GalleryRepositoryImpl(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val favoriteDao: FavoriteDao,
    private val preferencesManager: PreferencesManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : GalleryRepository {

    private val _rawLocalMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _albums = MutableStateFlow<List<Album>>(emptyList())

    override val favoriteIdsFlow: Flow<Set<String>> =
        favoriteDao.getAllFavoriteIds().map { it.toSet() }

    override val localMediaFlow: Flow<List<MediaItem>> =
        combine(_rawLocalMedia, favoriteIdsFlow) { mediaList, favIds ->
            mediaList.map { item ->
                item.copy(isFavorite = favIds.contains(item.id))
            }
        }

    override val albumsFlow: Flow<List<Album>> = _albums

    init {
        scope.launch {
            refreshLocalMedia()
        }
    }

    override suspend fun refreshLocalMedia() {
        val items = mediaStoreDataSource.getLocalMedia(includeVideos = true)
        _rawLocalMedia.value = items
        val albums = mediaStoreDataSource.getAlbums(items)
        _albums.value = albums
    }

    override suspend fun toggleFavorite(mediaId: String) {
        val isFav = favoriteDao.isFavorite(mediaId)
        if (isFav) {
            favoriteDao.removeFavorite(mediaId)
        } else {
            favoriteDao.addFavorite(FavoriteEntity(mediaId = mediaId))
        }
    }

    override suspend fun isFavorite(mediaId: String): Boolean {
        return favoriteDao.isFavorite(mediaId)
    }
}
