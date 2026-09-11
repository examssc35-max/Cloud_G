package com.example.domain.repository

import android.net.Uri
import com.example.domain.model.MediaItem
import com.example.domain.model.R2ConnectionStatus
import com.example.domain.model.R2Credentials
import com.example.domain.model.R2ObjectItem
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface CloudflareRepository {
    val connectionStatus: StateFlow<R2ConnectionStatus>
    fun getSavedCredentials(): R2Credentials?
    suspend fun testConnection(credentials: R2Credentials): Result<String>
    suspend fun connect(credentials: R2Credentials): Result<Unit>
    fun disconnect()
    fun clearCredentials()
    suspend fun listFolder(prefix: String = ""): Result<List<R2ObjectItem>>
    fun getMediaItemForCloudObject(item: R2ObjectItem): MediaItem?
    fun getPresignedUrl(key: String): String?
    suspend fun uploadLocalMedia(key: String, uri: Uri, mimeType: String, onProgress: ((Float) -> Unit)? = null): Result<Unit>
    suspend fun downloadObjectToPublic(key: String, onProgress: ((Float) -> Unit)? = null): Result<String>
    suspend fun deleteObject(key: String): Result<Unit>
    suspend fun createFolder(folderPath: String): Result<Unit>
}
