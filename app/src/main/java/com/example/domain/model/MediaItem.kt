package com.example.domain.model

import android.net.Uri

data class MediaItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val dateModified: Long,
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val isCloud: Boolean = false,
    val cloudKey: String? = null,
    val isFavorite: Boolean = false,
    val albumName: String = "All"
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return ""
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes >= 60) {
                val hours = minutes / 60
                val remMinutes = minutes % 60
                String.format("%d:%02d:%02d", hours, remMinutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var sizeDouble = size.toDouble()
            var unitIndex = 0
            while (sizeDouble >= 1024 && unitIndex < units.size - 1) {
                sizeDouble /= 1024
                unitIndex++
            }
            return String.format("%.1f %s", sizeDouble, units[unitIndex])
        }
}
