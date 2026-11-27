package com.lumeryx.app.library

import com.lumeryx.app.model.MediaItem
import com.lumeryx.app.model.MediaLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryModelsTest {
    @Test
    fun explicitlyReportedMimeTypeTakesPriority() {
        assertEquals(MediaKind.PHOTO, mediaKind("IMAGE/JPEG", "clip.mp4"))
        assertEquals(MediaKind.VIDEO, mediaKind("video/mp4", "unnamed"))
        assertNull(mediaKind("text/plain", "report.jpg"))
        assertNull(mediaKind("vnd.android.document/directory", "folder.mp4"))
    }

    @Test
    fun genericOrMissingMimeTypeUsesRecognizedExtension() {
        assertEquals(MediaKind.PHOTO, mediaKind(null, "photo.WEBP"))
        assertEquals(MediaKind.VIDEO, mediaKind("application/octet-stream", "movie.MP4"))
        assertNull(mediaKind(null, "notes.txt"))
        assertNull(mediaKind(null, "jpg"))
    }

    @Test
    fun unknownSizesAndDatesSortAfterKnownValues() {
        val items = listOf(
            item("a", "А", size = null, modified = null),
            item("b", "Б", size = 2, modified = 2000),
            item("c", "В", size = 9, modified = 1000),
        )

        assertEquals(listOf("c", "b", "a"), sortMedia(items, LibrarySort.SIZE).map { it.id })
        assertEquals(listOf("b", "c", "a"), sortMedia(items, LibrarySort.DATE).map { it.id })
        assertEquals(listOf("a", "b", "c"), sortMedia(items, LibrarySort.NAME).map { it.id })
    }

    @Test
    fun duplicateNamesHaveStableOrderInALargeFolder() {
        val contents = (0 until 2500).map { index ->
            item(index.toString().padStart(4, '0'), "Фото", 12, 3456)
        }

        for (order in LibrarySort.entries) {
            assertEquals(contents, sortMedia(contents.shuffled(kotlin.random.Random(42)), order))
        }
    }

    private fun item(id: String, name: String, size: Long?, modified: Long?) = MediaItem(
        id = id,
        location = MediaLocation("example.test", "content://example.test/document/$id"),
        displayName = name,
        mimeType = "image/jpeg",
        sizeBytes = size,
        modifiedAt = modified?.let(Instant::ofEpochMilli),
    )
}
