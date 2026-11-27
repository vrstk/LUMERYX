package com.lumeryx.app.transfer

import android.content.ContentResolver
import android.database.Cursor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import java.io.IOException

internal class SafCopyStorage(private val resolver: ContentResolver) : CopyStorage {
    fun root(tree: String): CopyDocument {
        val uri = tree.toUri()
        require(uri.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(uri))
        val root = readMetadata(
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)).toString(),
        )
        if (!root.isFolder) throw IOException("Selected document is not a folder")
        return root
    }

    override fun children(folder: CopyDocument): List<CopyDocument> {
        val uri = folder.uri.toUri()
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, folder.documentId)
        val cursor = resolver.query(childUri, columns, null, null, null)
            ?: throw IOException("Provider returned no listing")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID))
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    add(it.document(documentUri.toString(), folder.authority))
                }
            }
        }
    }

    override fun create(folder: CopyDocument, mimeType: String, name: String): CopyDocument {
        val uri = DocumentsContract.createDocument(resolver, folder.uri.toUri(), mimeType, name)
            ?: throw IOException("Provider could not create document")
        // Rebuild with the original tree grant when the provider omits it from its response.
        val treeUri = DocumentsContract.buildDocumentUriUsingTree(
            folder.uri.toUri(), DocumentsContract.getDocumentId(uri),
        )
        return try {
            readMetadata(treeUri.toString())
        } catch (error: Exception) {
            if (!runCatching { DocumentsContract.deleteDocument(resolver, treeUri) }.getOrDefault(false)) {
                error.addSuppressed(IOException("New document could not be cleaned up"))
            }
            throw error
        }
    }

    override fun rename(document: CopyDocument, name: String) {
        if (DocumentsContract.renameDocument(resolver, document.uri.toUri(), name) == null) {
            throw IOException("Provider could not rename document")
        }
    }

    override fun deleteNewDocument(document: CopyDocument) {
        if (!DocumentsContract.deleteDocument(resolver, document.uri.toUri())) {
            throw IOException("New document could not be cleaned up")
        }
    }

    override fun read(document: CopyDocument) =
        resolver.openInputStream(document.uri.toUri()) ?: throw IOException("Document is not readable")

    override fun write(document: CopyDocument) =
        resolver.openOutputStream(document.uri.toUri(), "w") ?: throw IOException("Document is not writable")

    private fun readMetadata(value: String): CopyDocument {
        val uri = value.toUri()
        val cursor = resolver.query(uri, columns, null, null, null)
            ?: throw IOException("Provider returned no document metadata")
        return cursor.use {
            if (!it.moveToFirst()) throw IOException("Document is unavailable")
            it.document(value, requireNotNull(uri.authority))
        }
    }

    private fun Cursor.document(uri: String, authority: String): CopyDocument {
        val id = getString(getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID))
        val mimeType = getString(getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE))
            ?: "application/octet-stream"
        val name = getString(getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME))
            ?: throw IOException("Document has no display name")
        val flags = getInt(getColumnIndexOrThrow(Document.COLUMN_FLAGS))
        val sizeIndex = getColumnIndexOrThrow(Document.COLUMN_SIZE)
        return CopyDocument(
            uri, authority, id, name, mimeType,
            size = if (isNull(sizeIndex)) null else getLong(sizeIndex).takeIf { it >= 0 },
            isFolder = mimeType == Document.MIME_TYPE_DIR,
            canCreate = flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0,
            canRename = flags and Document.FLAG_SUPPORTS_RENAME != 0,
            isVirtual = flags and Document.FLAG_VIRTUAL_DOCUMENT != 0,
        )
    }

    private companion object {
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
        )
    }
}
