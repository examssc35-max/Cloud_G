package com.example.ui.viewer

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.MediaItem
import com.example.domain.model.R2ConnectionStatus
import com.example.domain.repository.CloudflareRepository
import com.example.domain.repository.GalleryRepository
import com.example.ui.components.ConfirmationDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun MediaViewerScreen(
    mediaItem: MediaItem,
    galleryRepository: GalleryRepository,
    cloudflareRepository: CloudflareRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isControlsVisible by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(mediaItem.isFavorite) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isUploadingToR2 by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // Image Zoom & Pan states
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Initialize favorite status
    LaunchedEffect(mediaItem.id) {
        isFavorite = galleryRepository.isFavorite(mediaItem.id)
    }

    if (showDeleteConfirm) {
        ConfirmationDialog(
            title = "Delete Media?",
            message = "Are you sure you want to delete '${mediaItem.name}'? This action cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                scope.launch {
                    showDeleteConfirm = false
                    if (mediaItem.isCloud && mediaItem.cloudKey != null) {
                        cloudflareRepository.deleteObject(mediaItem.cloudKey)
                    }
                    onNavigateBack()
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = mediaItem.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Favorite toggle
                        IconButton(
                            onClick = {
                                scope.launch {
                                    galleryRepository.toggleFavorite(mediaItem.id)
                                    isFavorite = !isFavorite
                                }
                            },
                            modifier = Modifier.testTag("viewer_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFEF4444) else Color.White
                            )
                        }

                        // Share
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mediaItem.mimeType
                                    putExtra(Intent.EXTRA_STREAM, mediaItem.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${mediaItem.name}"))
                            },
                            modifier = Modifier.testTag("viewer_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }

                        // Info
                        IconButton(
                            onClick = { showInfoSheet = !showInfoSheet },
                            modifier = Modifier.testTag("viewer_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.65f)
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Details sheet if expanded
                        if (showInfoSheet) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Media Details",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DetailRow("Name", mediaItem.name)
                                    DetailRow("Size", mediaItem.formattedSize)
                                    DetailRow("Type", mediaItem.mimeType)
                                    if (mediaItem.width > 0 && mediaItem.height > 0) {
                                        DetailRow("Dimensions", "${mediaItem.width} × ${mediaItem.height}")
                                    }
                                    val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                        .format(Date(mediaItem.dateModified))
                                    DetailRow("Date", dateStr)
                                    DetailRow("Location", if (mediaItem.isCloud) "Cloudflare R2 (${mediaItem.cloudKey})" else "Local Device (${mediaItem.albumName})")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cloud Download or Upload action
                            if (mediaItem.isCloud && mediaItem.cloudKey != null) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            isDownloading = true
                                            val res = cloudflareRepository.downloadObjectToPublic(mediaItem.cloudKey)
                                            isDownloading = false
                                            if (res.isSuccess) {
                                                snackbarHostState.showSnackbar("Downloaded to ${res.getOrNull()}")
                                            } else {
                                                snackbarHostState.showSnackbar("Download failed: ${res.exceptionOrNull()?.localizedMessage}")
                                            }
                                        }
                                    },
                                    enabled = !isDownloading
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download", color = Color.White)
                                }
                            } else {
                                // Upload local item to Cloudflare R2
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            if (cloudflareRepository.connectionStatus.value !is R2ConnectionStatus.Connected) {
                                                snackbarHostState.showSnackbar("Please connect to Cloudflare R2 in Settings first.")
                                                return@launch
                                            }
                                            isUploadingToR2 = true
                                            val uploadResult = cloudflareRepository.uploadLocalMedia(
                                                key = mediaItem.name,
                                                uri = mediaItem.uri,
                                                mimeType = mediaItem.mimeType
                                            )
                                            isUploadingToR2 = false
                                            if (uploadResult.isSuccess) {
                                                snackbarHostState.showSnackbar("Uploaded to Cloudflare R2 bucket!")
                                            } else {
                                                snackbarHostState.showSnackbar("Upload failed: ${uploadResult.exceptionOrNull()?.localizedMessage}")
                                            }
                                        }
                                    },
                                    enabled = !isUploadingToR2
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isUploadingToR2) "Uploading..." else "Save to R2", color = Color.White)
                                }
                            }

                            // Delete Action
                            TextButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.testTag("viewer_delete_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete", color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (mediaItem.isVideo) {
                // ExoPlayer Video Player
                VideoPlayer(
                    uri = mediaItem.uri,
                    onToggleControls = { isControlsVisible = !isControlsVisible }
                )
            } else {
                // Zoomable & Pannable Photo Viewer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { isControlsVisible = !isControlsVisible },
                                onDoubleTap = {
                                    scale = if (scale > 1f) 1f else 2.5f
                                    offset = Offset.Zero
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    val maxOffsetX = (scale - 1f) * 500f
                                    val maxOffsetY = (scale - 1f) * 500f
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaItem.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = mediaItem.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }

            if (isUploadingToR2 || isDownloading) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isUploadingToR2) "Uploading to R2..." else "Downloading...",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: android.net.Uri,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setOnClickListener { onToggleControls() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
