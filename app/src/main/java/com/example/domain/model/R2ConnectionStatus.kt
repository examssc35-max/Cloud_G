package com.example.domain.model

sealed interface R2ConnectionStatus {
    data object Disconnected : R2ConnectionStatus
    data object Connecting : R2ConnectionStatus
    data class Connected(
        val accountId: String,
        val bucketName: String,
        val objectCount: Int? = null
    ) : R2ConnectionStatus
    data class Failed(
        val errorMessage: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : R2ConnectionStatus
}

data class R2Credentials(
    val accountId: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val bucketName: String = "",
    val customEndpoint: String? = null
) {
    val isValid: Boolean
        get() = accountId.isNotBlank() &&
                accessKeyId.isNotBlank() &&
                secretAccessKey.isNotBlank() &&
                bucketName.isNotBlank()
}
