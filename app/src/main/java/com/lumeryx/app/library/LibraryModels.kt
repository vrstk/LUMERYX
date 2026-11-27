package com.lumeryx.app.library

import com.lumeryx.app.model.MediaItem
import java.text.Collator
import java.util.Locale

internal data class LibraryFolder(val id: String, val name: String)

internal data class FolderContents(
    val folders: List<LibraryFolder>,
    val media: List<MediaItem>,
)

internal enum class MediaKind { PHOTO, VIDEO }

internal enum class LibrarySort { NAME, DATE, SIZE }

private val photoExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
private val videoExtensions = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp", "ts", "avi")

internal fun mediaKind(mime: String?, name: String): MediaKind? {
    val type = mime?.lowercase(Locale.ROOT)
    if (type?.startsWith("image/") == true) return MediaKind.PHOTO
    if (type?.startsWith("video/") == true) return MediaKind.VIDEO
    if (!type.isNullOrBlank() && type != "application/octet-stream") return null

    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (extension) {
        in photoExtensions -> MediaKind.PHOTO
        in videoExtensions -> MediaKind.VIDEO
        else -> null
    }
}

internal fun sortFolders(folders: List<LibraryFolder>): List<LibraryFolder> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply { strength = Collator.SECONDARY }
    return folders.sortedWith(compareBy(collator, LibraryFolder::name).thenBy(LibraryFolder::id))
}

internal fun sortMedia(items: List<MediaItem>, order: LibrarySort): List<MediaItem> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply { strength = Collator.SECONDARY }
    val byName = compareBy(collator, MediaItem::displayName).thenBy(MediaItem::id)
    return when (order) {
        LibrarySort.NAME -> items.sortedWith(byName)
        LibrarySort.DATE -> items.sortedWith(
            compareBy(nullsLast(reverseOrder()), MediaItem::modifiedAt).then(byName),
        )
        LibrarySort.SIZE -> items.sortedWith(
            compareBy(nullsLast(reverseOrder()), MediaItem::sizeBytes).then(byName),
        )
    }
}
