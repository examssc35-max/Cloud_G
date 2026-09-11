package com.example.data.cloudflare

import com.example.domain.model.R2Credentials
import com.example.domain.model.R2ObjectItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.InputStream
import java.io.OutputStream
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class R2Client(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    fun getEndpoint(credentials: R2Credentials): String {
        return if (!credentials.customEndpoint.isNullOrBlank()) {
            credentials.customEndpoint.trimEnd('/')
        } else {
            "https://${credentials.accountId}.r2.cloudflarestorage.com"
        }
    }

    suspend fun testConnection(credentials: R2Credentials): Result<String> = withContext(Dispatchers.IO) {
        if (!credentials.isValid) {
            return@withContext Result.failure(IllegalArgumentException("Please fill in all required fields."))
        }

        val endpoint = getEndpoint(credentials)
        val url = "$endpoint/${credentials.bucketName}?list-type=2&max-keys=1"

        try {
            val emptyBodyHash = R2Signer.sha256Hex(ByteArray(0))
            val signedHeaders = R2Signer.signRequest(
                method = "GET",
                url = url,
                headers = emptyMap(),
                payloadHash = emptyBodyHash,
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey
            )

            val requestBuilder = Request.Builder().url(url).get()
            signedHeaders.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        Result.success("Successfully connected to bucket '${credentials.bucketName}'!")
                    }
                    response.code == 401 || response.code == 403 -> {
                        Result.failure(Exception("Access Denied: Verify that your Access Key ID and Secret Access Key have Admin/Object Read permissions."))
                    }
                    response.code == 404 -> {
                        Result.failure(Exception("Bucket '${credentials.bucketName}' not found. Please verify the bucket name."))
                    }
                    else -> {
                        Result.failure(Exception("Connection error (HTTP ${response.code}): ${response.message}"))
                    }
                }
            }
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Could not resolve host. Please verify your Cloudflare Account ID and internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "Unknown error occurred."}"))
        }
    }

    suspend fun listObjects(
        credentials: R2Credentials,
        prefix: String = "",
        delimiter: String = "/",
        continuationToken: String? = null
    ): Result<Pair<List<R2ObjectItem>, String?>> = withContext(Dispatchers.IO) {
        val endpoint = getEndpoint(credentials)
        val queryParams = mutableListOf<String>()
        queryParams.add("list-type=2")
        queryParams.add("delimiter=${R2Signer.urlEncode(delimiter)}")
        if (prefix.isNotEmpty()) {
            queryParams.add("prefix=${R2Signer.urlEncode(prefix)}")
        }
        if (!continuationToken.isNullOrEmpty()) {
            queryParams.add("continuation-token=${R2Signer.urlEncode(continuationToken)}")
        }

        val url = "$endpoint/${credentials.bucketName}?${queryParams.joinToString("&")}"

        try {
            val emptyBodyHash = R2Signer.sha256Hex(ByteArray(0))
            val signedHeaders = R2Signer.signRequest(
                method = "GET",
                url = url,
                headers = emptyMap(),
                payloadHash = emptyBodyHash,
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey
            )

            val requestBuilder = Request.Builder().url(url).get()
            signedHeaders.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to list bucket (HTTP ${response.code}): ${response.message}"))
                }

                val xml = response.body?.string() ?: ""
                val parsed = R2XmlParser.parseListBucketResult(xml)

                val items = mutableListOf<R2ObjectItem>()

                // Add Folders (CommonPrefixes)
                parsed.commonPrefixes.forEach { folderPrefix ->
                    val folderName = folderPrefix.removeSuffix("/").substringAfterLast("/") + "/"
                    items.add(
                        R2ObjectItem(
                            key = folderPrefix,
                            name = folderName,
                            size = 0L,
                            lastModified = 0L,
                            isDirectory = true,
                            mimeType = "directory",
                            isImage = false,
                            isVideo = false
                        )
                    )
                }

                // Add Files
                parsed.contents.forEach { obj ->
                    // Skip directory placeholder objects (e.g. "photos/") if already listed as prefix
                    if (obj.key == prefix) return@forEach

                    val fileName = obj.key.substringAfterLast("/")
                    if (fileName.isNotEmpty()) {
                        val mimeType = detectMimeType(fileName)
                        val isImage = mimeType.startsWith("image/")
                        val isVideo = mimeType.startsWith("video/")

                        items.add(
                            R2ObjectItem(
                                key = obj.key,
                                name = fileName,
                                size = obj.size,
                                lastModified = obj.lastModified,
                                isDirectory = false,
                                mimeType = mimeType,
                                isImage = isImage,
                                isVideo = isVideo
                            )
                        )
                    }
                }

                Result.success(Pair(items, parsed.nextContinuationToken))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPresignedUrl(
        credentials: R2Credentials,
        key: String,
        expiresInSeconds: Long = 86400L // 24 hours
    ): String {
        val endpoint = getEndpoint(credentials)
        return R2Signer.generatePresignedUrl(
            endpoint = endpoint,
            bucket = credentials.bucketName,
            key = key,
            accessKeyId = credentials.accessKeyId,
            secretAccessKey = credentials.secretAccessKey,
            expiresInSeconds = expiresInSeconds
        )
    }

    suspend fun uploadData(
        credentials: R2Credentials,
        key: String,
        data: ByteArray,
        mimeType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = getEndpoint(credentials)
        val path = "/${credentials.bucketName}/${R2Signer.encodePath(key).trimStart('/')}"
        val url = "$endpoint$path"

        try {
            val payloadHash = R2Signer.sha256Hex(data)
            val baseHeaders = mapOf(
                "content-type" to mimeType
            )

            val signedHeaders = R2Signer.signRequest(
                method = "PUT",
                url = url,
                headers = baseHeaders,
                payloadHash = payloadHash,
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey
            )

            val requestBody = data.toRequestBody(mimeType.toMediaTypeOrNull())
            val requestBuilder = Request.Builder().url(url).put(requestBody)
            signedHeaders.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Upload failed (HTTP ${response.code}): ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadStream(
        credentials: R2Credentials,
        key: String,
        inputStream: InputStream,
        contentLength: Long,
        mimeType: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = getEndpoint(credentials)
        val path = "/${credentials.bucketName}/${R2Signer.encodePath(key).trimStart('/')}"
        val url = "$endpoint$path"

        try {
            // For streaming upload, we use UNSIGNED-PAYLOAD over HTTPS which S3/R2 standard supports
            val baseHeaders = mutableMapOf(
                "content-type" to mimeType,
                "content-length" to contentLength.toString()
            )

            val signedHeaders = R2Signer.signRequest(
                method = "PUT",
                url = url,
                headers = baseHeaders,
                payloadHash = "UNSIGNED-PAYLOAD",
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey
            )

            val countingBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()
                override fun contentLength() = contentLength
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(32 * 1024)
                    var uploaded = 0L
                    var read: Int
                    inputStream.use { stream ->
                        while (stream.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            uploaded += read
                            if (contentLength > 0) {
                                val progress = (uploaded.toFloat() / contentLength).coerceIn(0f, 1f)
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                }
            }

            val requestBuilder = Request.Builder().url(url).put(countingBody)
            signedHeaders.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Upload failed (HTTP ${response.code}): ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadObject(
        credentials: R2Credentials,
        key: String,
        outputStream: OutputStream,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = getEndpoint(credentials)
        val path = "/${credentials.bucketName}/${R2Signer.encodePath(key).trimStart('/')}"
        val url = "$endpoint$path"

        try {
            val emptyBodyHash = R2Signer.sha256Hex(ByteArray(0))
            val signedHeaders = R2Signer.signRequest(
                method = "GET",
                url = url,
                headers = emptyMap(),
                payloadHash = emptyBodyHash,
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey
            )

            val requestBuilder = Request.Builder().url(url).get()
            signedHeaders.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed (HTTP ${response.code}): ${response.message}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                val totalLength = body.contentLength()
                val inputStream = body.byteStream()
                val buffer = ByteArray(32 * 1024)
                var downloaded = 0L
                var read: Int

                outputStream.use { out ->
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (totalLength > 0) {
                            val progress = (downloaded.toFloat() / totalLength).coerceIn(0f, 1f)
                            onProgress?.invoke(progress)
                        }
                    }
                    out.flush()
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteObject(credentials: R2Credentials, key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = getEndpoint(credentials)
        val path = "/${credentials.bucketName}/${R2Signer.encodePath(key).trimStart('/')}"
        val url = "$endpoint$path"

        try {
            val emptyBodyHash = R2Signer.sha256Hex(ByteArray(0))
            val signedHeaders = R2Signer.signRequest(
                method = "DELETE",
                url = url,
                headers = emptyMap(),
                payloadHash = emptyBodyHash,
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey
            )

            val requestBuilder = Request.Builder().url(url).delete()
            signedHeaders.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Delete failed (HTTP ${response.code}): ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(credentials: R2Credentials, folderPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanKey = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
        uploadData(credentials, cleanKey, ByteArray(0), "application/x-directory")
    }

    fun detectMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "bmp" -> "image/bmp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
