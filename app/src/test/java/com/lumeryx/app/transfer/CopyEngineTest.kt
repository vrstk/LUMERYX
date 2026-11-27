package com.lumeryx.app.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyEngineTest {
    @Test
    fun copyFolderKeepsExistingFolderAndVerifiesEveryFile() = runBlocking {
        val storage = MemoryStorage()
        val source = storage.folder("Источник")
        val destination = storage.folder("Приёмник")
        val album = storage.folder("Альбом", source)
        val nested = storage.folder("Подпапка", album)
        storage.file("Фото.jpg", nested, byteArrayOf(12, 37, 10))
        storage.folder("АЛЬБОМ", destination)

        assertEquals(CopyResult.COPIED, CopyEngine(storage).copy(album, destination, CopyPolicy.KEEP_BOTH))
        val output = storage.children(destination).single { it.name == "Альбом (2)" }
        val copiedNested = storage.children(output).single()
        val photo = storage.children(copiedNested).single()
        assertEquals("Фото.jpg", photo.name)
        assertArrayEquals(byteArrayOf(12, 37, 10), storage.read(photo).readBytes())
        assertEquals(setOf("АЛЬБОМ", "Альбом (2)"), storage.children(destination).map { it.name }.toSet())
    }

    @Test
    fun skipDoesNotWriteAndSuffixPreservesExtension() = runBlocking {
        val storage = MemoryStorage()
        val source = storage.folder("Источник")
        val destination = storage.folder("Приёмник")
        val photo = storage.file("Фото.jpg", source, byteArrayOf(21))
        val existing = storage.file("фото.JPG", destination, byteArrayOf(9))
        val engine = CopyEngine(storage)

        assertTrue(engine.conflict(photo, destination))
        assertEquals(CopyResult.SKIPPED, engine.copy(photo, destination, CopyPolicy.SKIP))
        assertEquals(listOf(existing), storage.children(destination))
        assertEquals(CopyResult.COPIED, engine.copy(photo, destination, CopyPolicy.KEEP_BOTH))
        assertEquals(setOf("Фото (2).jpg", "фото.JPG"), storage.children(destination).map { it.name }.toSet())
        assertArrayEquals(byteArrayOf(9), storage.read(existing).readBytes())
    }

    @Test
    fun rejectsCopyIntoDescendantBeforeWriting() = runBlocking {
        val storage = MemoryStorage()
        val folder = storage.folder("Альбом")
        val subfolder = storage.folder("Подпапка", folder)
        val failure = runCatching { CopyEngine(storage).copy(folder, subfolder, CopyPolicy.ASK) }.exceptionOrNull()

        assertEquals(CopyProblem.OVERLAP, (failure as? CopyException)?.problem)
        assertTrue(storage.children(subfolder).isEmpty())
    }

    @Test
    fun corruptedWriteIsRemovedAndOriginalRemains() = runBlocking {
        val storage = MemoryStorage(corruptWrites = true, renameSupported = false)
        val source = storage.folder("Источник")
        val destination = storage.folder("Приёмник")
        val photo = storage.file("Фото.jpg", source, byteArrayOf(21))
        val failure = runCatching { CopyEngine(storage).copy(photo, destination, CopyPolicy.ASK) }.exceptionOrNull()

        assertEquals(CopyProblem.UNVERIFIED, (failure as? CopyException)?.problem)
        assertTrue(storage.children(destination).isEmpty())
        assertArrayEquals(byteArrayOf(21), storage.read(photo).readBytes())
    }

    @Test
    fun unexpectedConflictDoesNotOverwriteExistingFile() = runBlocking {
        val storage = MemoryStorage()
        val source = storage.folder("Источник")
        val destination = storage.folder("Приёмник")
        val photo = storage.file("Фото.jpg", source, byteArrayOf(21))
        val existing = storage.file("Фото.jpg", destination, byteArrayOf(9))
        val failure = runCatching { CopyEngine(storage).copy(photo, destination, CopyPolicy.ASK) }.exceptionOrNull()

        assertEquals(CopyProblem.CONFLICT, (failure as? CopyException)?.problem)
        assertEquals(listOf(existing), storage.children(destination))
        assertArrayEquals(byteArrayOf(9), storage.read(existing).readBytes())
    }

    private class MemoryStorage(
        private val corruptWrites: Boolean = false,
        private val renameSupported: Boolean = true,
    ) : CopyStorage {
        private val documents = mutableMapOf<String, CopyDocument>()
        private val parents = mutableMapOf<String, String?>()
        private val bytes = mutableMapOf<String, ByteArray>()
        private var nextId = 0

        fun folder(name: String, parent: CopyDocument? = null) =
            add(parent, DIRECTORY, name, null)

        fun file(name: String, parent: CopyDocument, payload: ByteArray) =
            add(parent, "image/jpeg", name, payload.size.toLong()).also { bytes[it.uri] = payload }

        private fun add(parent: CopyDocument?, mimeType: String, name: String, size: Long?): CopyDocument {
            val id = (++nextId).toString()
            val uri = "content://example.test/document/$id"
            val folder = mimeType == DIRECTORY
            val document = CopyDocument(uri, "example.test", id, name, mimeType, size, folder, folder, renameSupported)
            parents[uri] = parent?.uri
            documents[uri] = document
            return document
        }

        override fun children(folder: CopyDocument) =
            documents.values.filter { parents[it.uri] == folder.uri }

        override fun create(folder: CopyDocument, mimeType: String, name: String) =
            add(folder, mimeType, name, null)

        override fun rename(document: CopyDocument, name: String) {
            documents[document.uri] = document.copy(name = name)
        }

        override fun deleteNewDocument(document: CopyDocument) {
            documents.remove(document.uri)
            parents.remove(document.uri)
            bytes.remove(document.uri)
        }

        override fun read(document: CopyDocument) =
            ByteArrayInputStream(bytes[document.uri] ?: throw IOException("Missing fixture"))

        override fun write(document: CopyDocument) = object : ByteArrayOutputStream() {
            override fun close() {
                bytes[document.uri] = if (corruptWrites) byteArrayOf(0) else toByteArray()
            }
        }

        private companion object {
            const val DIRECTORY = "vnd.android.document/directory"
        }
    }
}
