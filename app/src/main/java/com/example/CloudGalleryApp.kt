package com.example

import android.app.Application
import com.example.data.local.MediaStoreDataSource
import com.example.data.local.PreferencesManager
import com.example.data.local.db.AppDatabase
import com.example.data.repository.CloudflareRepositoryImpl
import com.example.data.repository.GalleryRepositoryImpl
import com.example.domain.repository.CloudflareRepository
import com.example.domain.repository.GalleryRepository
import com.example.security.KeystoreManager

class CloudGalleryApp : Application() {

    lateinit var keystoreManager: KeystoreManager
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var galleryRepository: GalleryRepository
        private set

    lateinit var cloudflareRepository: CloudflareRepository
        private set

    override fun onCreate() {
        super.onCreate()
        keystoreManager = KeystoreManager(this)
        preferencesManager = PreferencesManager(this)

        val database = AppDatabase.getInstance(this)
        val mediaStoreDataSource = MediaStoreDataSource(this)

        galleryRepository = GalleryRepositoryImpl(
            mediaStoreDataSource = mediaStoreDataSource,
            favoriteDao = database.favoriteDao(),
            preferencesManager = preferencesManager
        )

        cloudflareRepository = CloudflareRepositoryImpl(
            context = this,
            keystoreManager = keystoreManager
        )
    }
}
