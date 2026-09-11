package com.example.ui.cloud

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.MediaItem
import com.example.domain.model.R2ConnectionStatus
import com.example.domain.model.R2ObjectItem
import com.example.domain.repository.CloudflareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CloudUiState(
    val currentPrefix: String = "", // e.g. "photos/vacation/"
    val items: List<R2ObjectItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val uploadProgress: Float? = null,
    val uploadingFileName: String? = null,
    val successMessage: String? = null,
    val deletingItem: R2ObjectItem? = null,
    val showCreateFolderDialog: Boolean = false
)

class CloudViewModel(
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudUiState())
    val uiState: StateFlow<CloudUiState> = _uiState.asStateFlow()

    val connectionStatus: StateFlow<R2ConnectionStatus> = cloudflareRepository.connectionStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), R2ConnectionStatus.Disconnected)

    init {
        loadFolder("")
    }

    fun loadFolder(prefix: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentPrefix = prefix,
                isLoading = true,
                errorMessage = null
            )
            val result = cloudflareRepository.listFolder(prefix)
            if (result.isSuccess) {
                val list = result.getOrNull().orEmpty()
                _uiState.value = _uiState.value.copy(
                    items = list,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to list folder contents."
                )
            }
        }
    }

    fun navigateIntoFolder(folderPrefix: String) {
        loadFolder(folderPrefix)
    }

    fun navigateUp() {
        val current = _uiState.value.currentPrefix.trimEnd('/')
        if (current.isEmpty()) return
        val lastSlash = current.lastIndexOf('/')
        val parentPrefix = if (lastSlash >= 0) current.substring(0, lastSlash + 1) else ""
        loadFolder(parentPrefix)
    }

    fun uploadFile(uri: Uri, mimeType: String, fileName: String) {
        viewModelScope.launch {
            val key = "${_uiState.value.currentPrefix}$fileName"
            _uiState.value = _uiState.value.copy(
                uploadingFileName = fileName,
                uploadProgress = 0f,
                errorMessage = null,
                successMessage = null
            )

            val result = cloudflareRepository.uploadLocalMedia(
                key = key,
                uri = uri,
                mimeType = mimeType,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(uploadProgress = progress)
                }
            )

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    uploadingFileName = null,
                    uploadProgress = null,
                    successMessage = "Successfully uploaded '$fileName'"
                )
                loadFolder(_uiState.value.currentPrefix)
            } else {
                _uiState.value = _uiState.value.copy(
                    uploadingFileName = null,
                    uploadProgress = null,
                    errorMessage = "Upload failed: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun setDeletingItem(item: R2ObjectItem?) {
        _uiState.value = _uiState.value.copy(deletingItem = item)
    }

    fun confirmDelete() {
        val item = _uiState.value.deletingItem ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingItem = null, isLoading = true)
            val result = cloudflareRepository.deleteObject(item.key)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Deleted '${item.name}'"
                )
                loadFolder(_uiState.value.currentPrefix)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Delete failed: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun downloadItem(item: R2ObjectItem, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = cloudflareRepository.downloadObjectToPublic(item.key)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isSuccess) {
                val path = result.getOrNull() ?: ""
                _uiState.value = _uiState.value.copy(successMessage = "Downloaded to $path")
                onComplete(path)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Download failed: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun showCreateFolderDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreateFolderDialog = show)
    }

    fun createFolder(folderName: String) {
        if (folderName.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showCreateFolderDialog = false, isLoading = true)
            val cleanName = folderName.trim().replace("/", "")
            val path = "${_uiState.value.currentPrefix}$cleanName/"
            val result = cloudflareRepository.createFolder(path)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Folder created")
                loadFolder(_uiState.value.currentPrefix)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to create folder: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun getMediaItem(item: R2ObjectItem): MediaItem? {
        return cloudflareRepository.getMediaItemForCloudObject(item)
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    class Factory(
        private val cloudflareRepository: CloudflareRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CloudViewModel(cloudflareRepository) as T
        }
    }
}
