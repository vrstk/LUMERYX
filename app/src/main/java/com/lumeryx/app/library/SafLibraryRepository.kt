package com.lumeryx.app.library

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.lumeryx.app.model.MediaItem
import com.lumeryx.app.model.MediaLocation
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class SafLibraryRepository(
    private val resolver: ContentResolver,
    private val dao: LibraryDao,
) {
    suspend fun selectedRoot(): Uri? = dao.selectedTreeUri()?.let(Uri::parse)

    suspend fun selectRoot(tree: Uri) = withContext(Dispatchers.IO + NonCancellable) {
        requireTreeUri(tree)
        val previous = selectedRoot()
        val alreadyGranted = hasReadPermission(tree)
        if (!alreadyGranted) resolver.takePersistableUriPermission(tree, READ_GRANT)

        var saved = false
        try {
            if (!hasReadPermission(tree)) throw SecurityException()
            dao.selectRoot(LibraryRootEntity(treeUri = tree.toString()))
            saved = true
        } finally {
            if (!saved && !alreadyGranted) releaseReadPermission(tree)
        }
        if (previous != null && previous != tree) releaseReadPermission(previous)
    }

    suspend fun readRoot(tree: Uri): LibraryFolder = withProviderQuery { signal ->
        requireReadPermission(tree)
        val id = DocumentsContract.getTreeDocumentId(tree)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val projection = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(documentUri, projection, null, null, null, signal)
            ?: throw IOException("Folder metadata unavailable")
        cursor.use {
            if (!it.moveToFirst() || it.text(Document.COLUMN_MIME_TYPE) != Document.MIME_TYPE_DIR) {
                throw IOException("Folder unavailable")
            }
            LibraryFolder(id, it.displayName(id))
        }
    }

    suspend fun readFolder(tree: Uri, parentId: String): FolderContents = withProviderQuery { signal ->
        requireReadPermission(tree)
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = resolver.query(childUri, columns, null, null, null, signal)
            ?: throw IOException("Folder listing unavailable")
        val folders = linkedMapOf<String, LibraryFolder>()
        val media = linkedMapOf<String, MediaItem>()

        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            while (it.moveToNext()) {
                signal.throwIfCanceled()
                val id = it.getString(idIndex)?.takeIf(String::isNotBlank) ?: continue
                if (id == parentId) continue
                val mime = it.text(Document.COLUMN_MIME_TYPE)
                val name = it.displayName(id)
                if (mime == Document.MIME_TYPE_DIR) {
                    folders.putIfAbsent(id, LibraryFolder(id, name))
                } else {
                    val flags = it.number(Document.COLUMN_FLAGS) ?: 0
                    if (flags and Document.FLAG_VIRTUAL_DOCUMENT.toLong() != 0L) continue
                    if (mediaKind(mime, name) == null) continue
                    media.putIfAbsent(
                        id,
                        mediaItem(
                            tree, id, name, mime,
                            sizeBytes = it.number(Document.COLUMN_SIZE)?.takeIf { bytes -> bytes >= 0 },
                            modifiedMillis = it.number(Document.COLUMN_LAST_MODIFIED)?.takeIf { ms -> ms > 0 },
                        ),
                    )
                }
            }
        }

        FolderContents(sortFolders(folders.values.toList()), media.values.toList())
    }

    fun favorites(tree: Uri): Flow<List<MediaItem>> = dao.favorites(tree.toString()).map { rows ->
        withContext(Dispatchers.Default) {
            rows.map { row ->
                mediaItem(
                    tree, row.documentId, row.displayName, row.mimeType,
                    row.sizeBytes, row.modifiedMillis,
                )
            }
        }
    }

    suspend fun setFavorite(tree: Uri, media: MediaItem, favorite: Boolean) {
        if (favorite) {
            dao.addFavorite(
                FavoriteEntity(
                    treeUri = tree.toString(),
                    documentId = media.id,
                    displayName = media.displayName,
                    mimeType = media.mimeType,
                    sizeBytes = media.sizeBytes,
                    modifiedMillis = media.modifiedAt?.toEpochMilli(),
                ),
            )
        } else {
            dao.removeFavorite(tree.toString(), media.id)
        }
    }

    private fun mediaItem(
        tree: Uri,
        id: String,
        name: String,
        mime: String?,
        sizeBytes: Long?,
        modifiedMillis: Long?,
    ): MediaItem = MediaItem(
        id = id,
        location = MediaLocation(
            providerId = requireNotNull(tree.authority),
            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
        ),
        displayName = name,
        mimeType = mime,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedMillis?.let(Instant::ofEpochMilli),
    )

    private fun requireReadPermission(tree: Uri) {
        requireTreeUri(tree)
        if (!hasReadPermission(tree)) throw SecurityException()
    }

    private fun requireTreeUri(tree: Uri) {
        require(tree.scheme == ContentResolver.SCHEME_CONTENT && !tree.authority.isNullOrBlank())
        require(DocumentsContract.isTreeUri(tree))
    }

    private fun hasReadPermission(tree: Uri): Boolean = resolver.persistedUriPermissions.any {
        it.uri == tree && it.isReadPermission
    }

    private fun releaseReadPermission(tree: Uri) {
        try {
            resolver.releasePersistableUriPermission(tree, READ_GRANT)
        } catch (_: SecurityException) {
            // The system or provider already revoked the grant.
        } catch (_: IllegalArgumentException) {
            // The old provider may have been removed.
        }
    }

    private suspend fun <T> withProviderQuery(query: (CancellationSignal) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            Dispatchers.IO.dispatch(continuation.context, Runnable {
                if (continuation.isActive) {
                    continuation.resumeWith(runCatching { query(signal) })
                }
            })
        }

    private fun Cursor.displayName(id: String): String =
        text(Document.COLUMN_DISPLAY_NAME)?.takeIf(String::isNotBlank)
            ?: id.substringAfterLast('/').substringAfterLast(':')

    private fun Cursor.text(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.number(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private companion object {
        const val READ_GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
