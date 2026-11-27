package com.lumeryx.app.transfer

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class CopyPolicy { ASK, SKIP, KEEP_BOTH }

internal enum class CopyProblem { CONFLICT, OVERLAP, UNSUPPORTED, UNVERIFIED, CLEANUP }

internal class CopyException(val problem: CopyProblem, cause: Throwable? = null) : IOException(null, cause)

internal enum class CopyResult { COPIED, SKIPPED }

internal class CopyEngine(private val storage: CopyStorage) {
    fun conflict(source: CopyDocument, destination: CopyDocument): Boolean =
        storage.children(destination).any { nameKey(it.name) == nameKey(source.name) }

    suspend fun copy(
        source: CopyDocument,
        destination: CopyDocument,
        policy: CopyPolicy,
        onBytes: (Long) -> Unit = {},
    ): CopyResult {
        if (!destination.canCreate) throw CopyException(CopyProblem.UNSUPPORTED)
        if (source.identity == destination.identity) throw CopyException(CopyProblem.OVERLAP)
        val targetName = availableName(source, destination, policy) ?: return CopyResult.SKIPPED
        val snapshot = snapshot(source, destination, mutableSetOf())
        var bytes = 0L
        copyNode(snapshot, destination, targetName) {
            bytes += it
            onBytes(bytes)
        }
        return CopyResult.COPIED
    }

    private suspend fun snapshot(
        source: CopyDocument,
        destination: CopyDocument,
        folders: MutableSet<Pair<String, String>>,
    ): CopyNode {
        currentCoroutineContext().ensureActive()
        if (source.identity == destination.identity) throw CopyException(CopyProblem.OVERLAP)
        if (source.isVirtual || !validName(source.name)) throw CopyException(CopyProblem.UNSUPPORTED)
        if (!source.isFolder) return CopyNode(source)
        if (!folders.add(source.identity)) throw CopyException(CopyProblem.OVERLAP)
        val children = storage.children(source).map { snapshot(it, destination, folders) }
        return CopyNode(source, children)
    }

    private suspend fun copyNode(
        node: CopyNode,
        destination: CopyDocument,
        name: String,
        onBytes: (Long) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        if (!node.document.isFolder) {
            copyFile(node.document, destination, name, onBytes)
            return
        }

        ensureFreeName(destination, name)
        val createdFolder = storage.create(destination, node.document.mimeType, name)
        for (child in node.children) {
            val childName = availableName(child.document, createdFolder, CopyPolicy.KEEP_BOTH)
                ?: error("Keep both always picks a name")
            copyNode(child, createdFolder, childName, onBytes)
        }
    }

    private suspend fun copyFile(
        source: CopyDocument,
        destination: CopyDocument,
        name: String,
        onBytes: (Long) -> Unit,
    ) {
        ensureFreeName(destination, name)
        val temporary = storage.create(destination, source.mimeType, "LUMERYX-part-${UUID.randomUUID()}")
        val staged = temporary.canRename
        val target = if (staged) temporary else {
            clean(temporary)
            ensureFreeName(destination, name)
            storage.create(destination, source.mimeType, name)
        }

        try {
            val expected = storage.read(source).use { input ->
                storage.write(target).use { output ->
                    digest(input) { buffer, count ->
                        output.write(buffer, 0, count)
                        onBytes(count.toLong())
                    }
                }
            }
            if (source.size != null && source.size != expected.size) {
                throw CopyException(CopyProblem.UNVERIFIED)
            }
            val actual = storage.read(target).use { digest(it) }
            if (expected.size != actual.size || !expected.hash.contentEquals(actual.hash)) {
                throw CopyException(CopyProblem.UNVERIFIED)
            }
            currentCoroutineContext().ensureActive()
            if (staged) {
                ensureFreeName(destination, name)
                storage.rename(target, name)
            }
        } catch (failure: Exception) {
            try {
                clean(target)
            } catch (cleanup: CopyException) {
                cleanup.addSuppressed(failure)
                throw cleanup
            }
            throw failure
        }
    }

    private fun clean(document: CopyDocument) {
        try {
            storage.deleteNewDocument(document)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failed: Exception) {
            throw CopyException(CopyProblem.CLEANUP, failed)
        }
    }

    private suspend fun digest(
        input: InputStream,
        onChunk: (ByteArray, Int) -> Unit = { _, _ -> },
    ): Digest {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var bytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
            bytes += count
            onChunk(buffer, count)
        }
        return Digest(bytes, hash.digest())
    }

    private fun ensureFreeName(folder: CopyDocument, name: String) {
        if (storage.children(folder).any { nameKey(it.name) == nameKey(name) }) {
            throw CopyException(CopyProblem.CONFLICT)
        }
    }

    private fun availableName(
        source: CopyDocument,
        destination: CopyDocument,
        policy: CopyPolicy,
    ): String? {
        val existing = storage.children(destination).mapTo(mutableSetOf()) { nameKey(it.name) }
        return chooseCopyName(source.name, source.isFolder, existing, policy)
    }

    private data class CopyNode(val document: CopyDocument, val children: List<CopyNode> = emptyList())
    private data class Digest(val size: Long, val hash: ByteArray)
}

internal fun nameKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)
    .lowercase(Locale.ROOT)

internal fun chooseCopyName(
    name: String,
    isFolder: Boolean,
    existing: Set<String>,
    policy: CopyPolicy,
): String? {
    if (!validName(name)) throw CopyException(CopyProblem.UNSUPPORTED)
    if (nameKey(name) !in existing) return name
    return when (policy) {
        CopyPolicy.ASK -> throw CopyException(CopyProblem.CONFLICT)
        CopyPolicy.SKIP -> null
        CopyPolicy.KEEP_BOTH -> {
            val dot = if (isFolder) -1 else name.lastIndexOf('.')
            val stem = if (dot > 0) name.substring(0, dot) else name
            val extension = if (dot > 0) name.substring(dot) else ""
            generateSequence(2) { it + 1 }
                .map { "$stem ($it)$extension" }
                .first { nameKey(it) !in existing }
        }
    }
}

private fun validName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it.isISOControl() }
