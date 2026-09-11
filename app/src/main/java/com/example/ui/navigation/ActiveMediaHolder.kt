package com.example.ui.navigation

import com.example.domain.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveMediaHolder {
    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    val currentMedia = _currentMedia.asStateFlow()

    fun setMedia(item: MediaItem?) {
        _currentMedia.value = item
    }
}
