package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.GallerySortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gallery_settings")

enum class ThemeSetting {
    SYSTEM, LIGHT, DARK
}

class PreferencesManager(private val context: Context) {

    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
    private val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
    private val KEY_SHOW_VIDEOS = booleanPreferencesKey("show_videos")

    val themeFlow: Flow<ThemeSetting> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_THEME] ?: ThemeSetting.SYSTEM.name
        try {
            ThemeSetting.valueOf(name)
        } catch (_: Exception) {
            ThemeSetting.SYSTEM
        }
    }

    val gridColumnsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_GRID_COLUMNS] ?: 3
    }

    val sortOrderFlow: Flow<GallerySortOrder> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_SORT_ORDER] ?: GallerySortOrder.DATE_DESC.name
        try {
            GallerySortOrder.valueOf(name)
        } catch (_: Exception) {
            GallerySortOrder.DATE_DESC
        }
    }

    val showVideosFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_VIDEOS] ?: true
    }

    suspend fun setTheme(theme: ThemeSetting) {
        context.dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[KEY_GRID_COLUMNS] = columns.coerceIn(2, 5) }
    }

    suspend fun setSortOrder(order: GallerySortOrder) {
        context.dataStore.edit { it[KEY_SORT_ORDER] = order.name }
    }

    suspend fun setShowVideos(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_VIDEOS] = show }
    }
}
