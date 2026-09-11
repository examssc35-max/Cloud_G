package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.domain.model.Album
import com.example.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreDataSource(private val context: Context) {

    suspend fun getLocalMedia(includeVideos: Boolean = true): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaItem>()

            // Query Photos
            try {
                val imageProjection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
                )

                val imageSortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageProjection,
                    null,
                    null,
                    imageSortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = if (nameCol != -1) cursor.getString(nameCol) ?: "Image_$id" else "Image_$id"
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                        val dateModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                        val bucket = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Camera" else "Camera"
                        val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                        val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                        val contentUri: Uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        mediaList.add(
                            MediaItem(
                                id = "local_img_$id",
                                uri = contentUri,
                                name = name,
                                size = size,
                                mimeType = mimeType,
                                dateModified = dateModified,
                                isVideo = false,
                                durationMs = 0L,
                                width = width,
                                height = height,
                                isCloud = false,
                                albumName = bucket
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Permission not granted or query error
            }

            // Query Videos if enabled
            if (includeVideos) {
                try {
                    val videoProjection = arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.MIME_TYPE,
                        MediaStore.Video.Media.DATE_MODIFIED,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                        MediaStore.Video.Media.DURATION,
                        MediaStore.Video.Media.WIDTH,
                        MediaStore.Video.Media.HEIGHT
                    )

                    val videoSortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        videoProjection,
                        null,
                        null,
                        videoSortOrder
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                        val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                        val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                        val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                        val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                        val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                        val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val name = if (nameCol != -1) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                            val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                            val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                            val dateModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                            val bucket = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Videos" else "Videos"
                            val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                            val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                            val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                            val contentUri: Uri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                            mediaList.add(
                                MediaItem(
                                    id = "local_vid_$id",
                                    uri = contentUri,
                                    name = name,
                                    size = size,
                                    mimeType = mimeType,
                                    dateModified = dateModified,
                                    isVideo = true,
                                    durationMs = duration,
                                    width = width,
                                    height = height,
                                    isCloud = false,
                                    albumName = bucket
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Permission not granted or query error
                }
            }

            mediaList.sortedByDescending { it.dateModified }
        }

    suspend fun getAlbums(mediaList: List<MediaItem>): List<Album> = withContext(Dispatchers.Default) {
        val grouped = mediaList.groupBy { it.albumName }
        grouped.map { (albumName, items) ->
            Album(
                id = albumName,
                name = albumName,
                count = items.size,
                coverUri = items.firstOrNull()?.uri,
                isCloud = false
            )
        }.sortedByDescending { it.count }
    }
}
