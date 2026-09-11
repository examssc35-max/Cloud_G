package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.CloudGalleryApp
import com.example.ui.cloud.CloudBrowserScreen
import com.example.ui.cloud.CloudViewModel
import com.example.ui.gallery.GalleryScreen
import com.example.ui.gallery.GalleryViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.viewer.MediaViewerScreen

const val ROUTE_GALLERY = "gallery"
const val ROUTE_VIEWER = "viewer"
const val ROUTE_CLOUD = "cloud"
const val ROUTE_SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudGalleryApp

    val galleryViewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModel.Factory(
            galleryRepository = app.galleryRepository,
            cloudflareRepository = app.cloudflareRepository,
            preferencesManager = app.preferencesManager
        )
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            cloudflareRepository = app.cloudflareRepository,
            preferencesManager = app.preferencesManager
        )
    )

    val cloudViewModel: CloudViewModel = viewModel(
        factory = CloudViewModel.Factory(
            cloudflareRepository = app.cloudflareRepository
        )
    )

    // Permissions logic
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        galleryViewModel.setPermissionGranted(granted)
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        galleryViewModel.setPermissionGranted(allGranted)
        if (!allGranted) {
            permissionLauncher.launch(permissions)
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_GALLERY,
        modifier = modifier
    ) {
        composable(ROUTE_GALLERY) {
            GalleryScreen(
                viewModel = galleryViewModel,
                onMediaClick = { item ->
                    ActiveMediaHolder.setMedia(item)
                    navController.navigate(ROUTE_VIEWER)
                },
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                onRequestPermission = { permissionLauncher.launch(permissions) },
                onOpenCloudExplorer = { navController.navigate(ROUTE_CLOUD) }
            )
        }

        composable(ROUTE_VIEWER) {
            val activeItem by ActiveMediaHolder.currentMedia.collectAsStateWithLifecycle()
            if (activeItem != null) {
                MediaViewerScreen(
                    mediaItem = activeItem!!,
                    galleryRepository = app.galleryRepository,
                    cloudflareRepository = app.cloudflareRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable(ROUTE_CLOUD) {
            CloudBrowserScreen(
                viewModel = cloudViewModel,
                onMediaClick = { item ->
                    ActiveMediaHolder.setMedia(item)
                    navController.navigate(ROUTE_VIEWER)
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
