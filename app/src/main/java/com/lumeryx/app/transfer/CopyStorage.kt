package com.lumeryx.app.transfer

import java.io.InputStream
import java.io.OutputStream

internal data class CopyDocument(
    val uri: String,
    val authority: String,
    val documentId: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val isFolder: Boolean = false,
    val canCreate: Boolean = false,
    val canRename: Boolean = false,
    val isVirtual: Boolean = false,
) {
    val identity: Pair<String, String> get() = authority to documentId
}

internal interface CopyStorage {
    fun children(folder: CopyDocument): List<CopyDocument>
    fun create(folder: CopyDocument, mimeType: String, name: String): CopyDocument
    fun rename(document: CopyDocument, name: String)
    fun deleteNewDocument(document: CopyDocument)
    fun read(document: CopyDocument): InputStream
    fun write(document: CopyDocument): OutputStream
}
