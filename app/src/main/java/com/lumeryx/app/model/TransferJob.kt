package com.lumeryx.app.model

enum class TransferStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class TransferJob(
    val id: String,
    val source: MediaLocation,
    val destination: MediaLocation,
    val status: TransferStatus = TransferStatus.QUEUED,
    val transferredBytes: Long = 0,
    val totalBytes: Long? = null,
    val retries: Int = 0,
    val error: String? = null,
) {
    init {
        require(transferredBytes >= 0)
        require(totalBytes == null || totalBytes >= transferredBytes)
        require(retries >= 0)
    }
}
