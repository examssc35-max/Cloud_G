package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.cloudflare.R2Client
import com.example.domain.model.MediaItem
import com.example.domain.model.R2ConnectionStatus
import com.example.domain.model.R2Credentials
import com.example.domain.model.R2ObjectItem
import com.example.domain.repository.CloudflareRepository
import com.example.security.KeystoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CloudflareRepositoryImpl(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val r2Client: R2Client = R2Client(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CloudflareRepository {

    private val _connectionStatus = MutableStateFlow<R2ConnectionStatus>(R2ConnectionStatus.Disconnected)
    override val connectionStatus: StateFlow<R2ConnectionStatus> = _connectionStatus.asStateFlow()

    init {
        // Check for saved credentials on launch
        val saved = keystoreManager.getR2Credentials()
        if (saved != null) {
            _connectionStatus.value = R2ConnectionStatus.Connected(
                accountId = saved.accountId,
                bucketName = saved.bucketName
            )
        }
    }

    override fun getSavedCredentials(): R2Credentials? {
        val stored = keystoreManager.getR2Credentials() ?: return null
        return R2Credentials(
            accountId = stored.accountId,
            accessKeyId = stored.accessKeyId,
            secretAccessKey = stored.secretAccessKey,
            bucketName = stored.bucketName,
            customEndpoint = stored.customEndpoint
        )
    }

    override suspend fun testConnection(credentials: R2Credentials): Result<String> {
        return r2Client.testConnection(credentials)
    }

    override suspend fun connect(credentials: R2Credentials): Result<Unit> {
        _connectionStatus.value = R2ConnectionStatus.Connecting
        val testResult = r2Client.testConnection(credentials)
        return if (testResult.isSuccess) {
            keystoreManager.saveR2Credentials(
                accountId = credentials.accountId,
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey,
                bucketName = credentials.bucketName,
                customEndpoint = credentials.customEndpoint
            )
            _connectionStatus.value = R2ConnectionStatus.Connected(
                accountId = credentials.accountId,
                bucketName = credentials.bucketName
            )
            Result.success(Unit)
        } else {
            val errorMsg = testResult.exceptionOrNull()?.message ?: "Connection failed"
            _connectionStatus.value = R2ConnectionStatus.Failed(errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    override fun disconnect() {
        _connectionStatus.value = R2ConnectionStatus.Disconnected
    }

    override fun clearCredentials() {
        keystoreManager.clearAll()
        _connectionStatus.value = R2ConnectionStatus.Disconnected
    }

    override suspend fun listFolder(prefix: String): Result<List<R2ObjectItem>> {
        val creds = getSavedCredentials()
            ?: return Result.failure(IllegalStateException("Not connected to Cloudflare R2."))

        return r2Client.listObjects(creds, prefix = prefix).map { it.first }
    }

    override fun getPresignedUrl(key: String): String? {
        val creds = getSavedCredentials() ?: return null
        return r2Client.getPresignedUrl(creds, key)
    }

    override fun getMediaItemForCloudObject(item: R2ObjectItem): MediaItem? {
        val presignedUrl = getPresignedUrl(item.key) ?: return null
        val uri = Uri.parse(presignedUrl)
        return MediaItem(
            id = "r2_${item.key}",
            uri = uri,
            name = item.name,
            size = item.size,
            mimeType = item.mimeType,
            dateModified = item.lastModified,
            isVideo = item.isVideo,
            durationMs = 0L,
            isCloud = true,
            cloudKey = item.key,
            albumName = "Cloud"
        )
    }

    override suspend fun uploadLocalMedia(
        key: String,
        uri: Uri,
        mimeType: String,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val creds = getSavedCredentials()
            ?: return@withContext Result.failure(IllegalStateException("Cloudflare R2 is not connected."))

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open selected file for reading."))

            var size = 0L
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (size <= 0) {
                // Read into memory if size unknown or small
                val bytes = inputStream.use { it.readBytes() }
                return@withContext r2Client.uploadData(creds, key, bytes, mimeType)
            }

            r2Client.uploadStream(creds, key, inputStream, size, mimeType, onProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadObjectToPublic(
        key: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> = withContext(Dispatchers.IO) {
        val creds = getSavedCredentials()
            ?: return@withContext Result.failure(IllegalStateException("Cloudflare R2 is not connected."))

        val fileName = key.substringAfterLast("/")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir

        val targetFile = File(downloadsDir, fileName)

        try {
            val outputStream = FileOutputStream(targetFile)
            val result = r2Client.downloadObject(creds, key, outputStream, onProgress)
            if (result.isSuccess) {
                Result.success(targetFile.absolutePath)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Download failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteObject(key: String): Result<Unit> {
        val creds = getSavedCredentials()
            ?: return Result.failure(IllegalStateException("Cloudflare R2 is not connected."))
        return r2Client.deleteObject(creds, key)
    }

    override suspend fun createFolder(folderPath: String): Result<Unit> {
        val creds = getSavedCredentials()
            ?: return Result.failure(IllegalStateException("Cloudflare R2 is not connected."))
        return r2Client.createFolder(creds, folderPath)
    }
}
