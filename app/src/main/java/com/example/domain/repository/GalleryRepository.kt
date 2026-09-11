package com.example.domain.repository

import com.example.domain.model.Album
import com.example.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface GalleryRepository {
    val localMediaFlow: Flow<List<MediaItem>>
    val albumsFlow: Flow<List<Album>>
    val favoriteIdsFlow: Flow<Set<String>>
    suspend fun refreshLocalMedia()
    suspend fun toggleFavorite(mediaId: String)
    suspend fun isFavorite(mediaId: String): Boolean
}
