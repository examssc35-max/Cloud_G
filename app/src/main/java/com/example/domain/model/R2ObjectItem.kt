package com.example.domain.model

data class R2ObjectItem(
    val key: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String,
    val isImage: Boolean,
    val isVideo: Boolean
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "Folder"
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var sizeDouble = size.toDouble()
            var unitIndex = 0
            while (sizeDouble >= 1024 && unitIndex < units.size - 1) {
                sizeDouble /= 1024
                unitIndex++
            }
            return String.format("%.1f %s", sizeDouble, units[unitIndex])
        }
}
