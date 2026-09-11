package com.example.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.GallerySortOrder
import com.example.domain.model.GalleryTab
import com.example.domain.model.MediaItem
import com.example.domain.model.R2ConnectionStatus
import com.example.ui.components.AlbumCard
import com.example.ui.components.EmptyState
import com.example.ui.components.MediaGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenCloudExplorer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val r2Status by viewModel.r2Status.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (uiState.isSearchActive) {
                // Search Top Bar
                TopAppBar(
                    title = {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search photos, videos...", fontSize = 16.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.setSearchActive(false) },
                            modifier = Modifier.testTag("search_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                // Regular Top Bar
                TopAppBar(
                    title = {
                        if (uiState.selectedAlbum != null) {
                            Text(
                                text = uiState.selectedAlbum!!,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CloudGallery",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    )
                                )
                                if (r2Status is R2ConnectionStatus.Connected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "R2 Active",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (uiState.selectedAlbum != null) {
                            IconButton(
                                onClick = { viewModel.selectAlbum(null) },
                                modifier = Modifier.testTag("album_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to albums")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.setSearchActive(true) },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search media")
                        }

                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort media")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                GallerySortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.displayName) },
                                        trailingIcon = {
                                            RadioButton(
                                                selected = (sortOrder == order),
                                                onClick = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.testTag("refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh gallery")
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Banner if media permission not yet granted
            AnimatedVisibility(visible = !uiState.hasPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Permission Required",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CloudGallery needs access to photos and videos to display your local media library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Text("Grant Access")
                        }
                    }
                }
            }

            // Tabs Row (only visible if not drilled down into an album)
            if (uiState.selectedAlbum == null) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    GalleryTab.entries.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (tab) {
                                            GalleryTab.ALL -> Icons.Default.Collections
                                            GalleryTab.PHOTOS -> Icons.Default.Image
                                            GalleryTab.VIDEOS -> Icons.Default.Videocam
                                            GalleryTab.ALBUMS -> Icons.Default.Folder
                                            GalleryTab.CLOUD -> Icons.Default.Cloud
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(tab.title)
                                }
                            },
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            // Cloud Tab Banner / Shortcut to Folder Explorer
            if (uiState.selectedTab == GalleryTab.CLOUD && r2Status is R2ConnectionStatus.Connected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val bucket = (r2Status as R2ConnectionStatus.Connected).bucketName
                            Text(
                                text = "Cloudflare R2: $bucket",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Browse folders, upload & manage files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Button(
                            onClick = onOpenCloudExplorer,
                            modifier = Modifier.testTag("open_cloud_explorer_button")
                        ) {
                            Text("Explore", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    // Albums Tab (when no specific album is drilled down)
                    uiState.selectedTab == GalleryTab.ALBUMS && uiState.selectedAlbum == null -> {
                        if (albums.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.Folder,
                                title = "No albums found",
                                message = "Albums are automatically created based on folders on your device."
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                contentPadding = PaddingValues(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(albums, key = { it.id }) { album ->
                                    AlbumCard(
                                        album = album,
                                        onClick = { viewModel.selectAlbum(album.name) }
                                    )
                                }
                            }
                        }
                    }

                    // Cloud Tab Disconnected State
                    uiState.selectedTab == GalleryTab.CLOUD && r2Status !is R2ConnectionStatus.Connected -> {
                        EmptyState(
                            icon = Icons.Default.CloudOff,
                            title = "Cloud storage isn't connected",
                            message = "Connect your personal Cloudflare R2 bucket in settings to browse and manage cloud photos securely.",
                            actionButtonText = "Connect Cloudflare R2",
                            onActionClick = onNavigateToSettings
                        )
                    }

                    // Empty Media List
                    mediaItems.isEmpty() -> {
                        val emptyTitle = when (uiState.selectedTab) {
                            GalleryTab.PHOTOS -> "No photos yet"
                            GalleryTab.VIDEOS -> "No videos yet"
                            GalleryTab.CLOUD -> "This bucket is empty."
                            else -> "No photos or videos"
                        }
                        val emptyMsg = when (uiState.selectedTab) {
                            GalleryTab.CLOUD -> "Upload photos and videos to your Cloudflare R2 bucket to see them here."
                            else -> "Your media will automatically appear here once captured or downloaded."
                        }
                        EmptyState(
                            icon = when (uiState.selectedTab) {
                                GalleryTab.VIDEOS -> Icons.Default.Videocam
                                GalleryTab.CLOUD -> Icons.Default.Cloud
                                else -> Icons.Default.PermMedia
                            },
                            title = emptyTitle,
                            message = emptyMsg
                        )
                    }

                    // Media Grid Display
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(2.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("media_grid")
                        ) {
                            items(mediaItems, key = { it.id }) { item ->
                                MediaGridItem(
                                    item = item,
                                    onClick = { onMediaClick(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
