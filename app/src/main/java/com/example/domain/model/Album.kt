package com.example.domain.model

import android.net.Uri

data class Album(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: Uri? = null,
    val isCloud: Boolean = false
)
