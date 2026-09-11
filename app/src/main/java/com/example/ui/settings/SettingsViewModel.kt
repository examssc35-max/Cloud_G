package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.ThemeSetting
import com.example.domain.model.GallerySortOrder
import com.example.domain.model.R2ConnectionStatus
import com.example.domain.model.R2Credentials
import com.example.domain.repository.CloudflareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accountId: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val bucketName: String = "",
    val customEndpoint: String = "",
    val isTesting: Boolean = false,
    val testResultMessage: String? = null,
    val isTestSuccess: Boolean = false,
    val isSecretVisible: Boolean = false
)

class SettingsViewModel(
    private val cloudflareRepository: CloudflareRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val connectionStatus: StateFlow<R2ConnectionStatus> = cloudflareRepository.connectionStatus

    val themeSetting: StateFlow<ThemeSetting> = preferencesManager.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSetting.SYSTEM)

    val gridColumns: StateFlow<Int> = preferencesManager.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val sortOrder: StateFlow<GallerySortOrder> = preferencesManager.sortOrderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GallerySortOrder.DATE_DESC)

    val showVideos: StateFlow<Boolean> = preferencesManager.showVideosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        val creds = cloudflareRepository.getSavedCredentials()
        if (creds != null) {
            _uiState.value = _uiState.value.copy(
                accountId = creds.accountId,
                accessKeyId = creds.accessKeyId,
                secretAccessKey = creds.secretAccessKey,
                bucketName = creds.bucketName,
                customEndpoint = creds.customEndpoint ?: ""
            )
        }
    }

    fun updateAccountId(value: String) {
        _uiState.value = _uiState.value.copy(accountId = value, testResultMessage = null)
    }

    fun updateAccessKeyId(value: String) {
        _uiState.value = _uiState.value.copy(accessKeyId = value, testResultMessage = null)
    }

    fun updateSecretAccessKey(value: String) {
        _uiState.value = _uiState.value.copy(secretAccessKey = value, testResultMessage = null)
    }

    fun updateBucketName(value: String) {
        _uiState.value = _uiState.value.copy(bucketName = value, testResultMessage = null)
    }

    fun updateCustomEndpoint(value: String) {
        _uiState.value = _uiState.value.copy(customEndpoint = value, testResultMessage = null)
    }

    fun toggleSecretVisibility() {
        _uiState.value = _uiState.value.copy(isSecretVisible = !_uiState.value.isSecretVisible)
    }

    fun testConnection() {
        val creds = buildCredentials()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResultMessage = null)
            val result = cloudflareRepository.testConnection(creds)
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                isTestSuccess = result.isSuccess,
                testResultMessage = if (result.isSuccess) {
                    result.getOrNull() ?: "Connection successful!"
                } else {
                    result.exceptionOrNull()?.localizedMessage ?: "Connection failed."
                }
            )
        }
    }

    fun saveAndConnect() {
        val creds = buildCredentials()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResultMessage = null)
            val result = cloudflareRepository.connect(creds)
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                isTestSuccess = result.isSuccess,
                testResultMessage = if (result.isSuccess) {
                    "Connected and credentials encrypted with Android Keystore!"
                } else {
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to connect."
                }
            )
        }
    }

    fun disconnect() {
        cloudflareRepository.disconnect()
        _uiState.value = _uiState.value.copy(testResultMessage = "Disconnected from Cloudflare R2.")
    }

    fun clearCredentials() {
        cloudflareRepository.clearCredentials()
        _uiState.value = SettingsUiState(testResultMessage = "Credentials purged from encrypted storage.")
    }

    fun setTheme(theme: ThemeSetting) {
        viewModelScope.launch {
            preferencesManager.setTheme(theme)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            preferencesManager.setGridColumns(columns)
        }
    }

    fun setSortOrder(order: GallerySortOrder) {
        viewModelScope.launch {
            preferencesManager.setSortOrder(order)
        }
    }

    fun setShowVideos(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.setShowVideos(show)
        }
    }

    private fun buildCredentials(): R2Credentials {
        val s = _uiState.value
        return R2Credentials(
            accountId = s.accountId.trim(),
            accessKeyId = s.accessKeyId.trim(),
            secretAccessKey = s.secretAccessKey.trim(),
            bucketName = s.bucketName.trim(),
            customEndpoint = if (s.customEndpoint.isNotBlank()) s.customEndpoint.trim() else null
        )
    }

    class Factory(
        private val cloudflareRepository: CloudflareRepository,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(cloudflareRepository, preferencesManager) as T
        }
    }
}
