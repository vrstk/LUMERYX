package com.lumeryx.app.model

import java.time.Instant

data class MediaLocation(
    val providerId: String,
    val uri: String,
    val remoteId: String? = null,
)

data class MediaItem(
    val id: String,
    val location: MediaLocation,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val sha256: String? = null,
    val createdAt: Instant? = null,
    val modifiedAt: Instant? = null,
) {
    init {
        require(sizeBytes == null || sizeBytes >= 0)
    }
}

data class MediaCollection(
    val id: String,
    val title: String,
)

data class Person(
    val id: String,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
)

data class Tag(val id: String, val name: String)

data class SourceBookmark(
    val id: String,
    val title: String,
    val url: String,
    val createdAt: Instant,
)
