package com.example.domain.model

enum class GallerySortOrder(val displayName: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first")
}

enum class GalleryTab(val title: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    ALBUMS("Albums"),
    CLOUD("Cloud")
}
