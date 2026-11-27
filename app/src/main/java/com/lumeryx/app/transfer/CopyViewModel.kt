package com.lumeryx.app.transfer

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class CopyStatus {
    PICKER_UNAVAILABLE, FOLDER_FAILED, COPIED, SKIPPED, FAILED, CANCELLED,
    OVERLAP, UNSUPPORTED, UNVERIFIED, CLEANUP, JOURNAL_FAILED,
}

internal data class CopyReview(val document: CopyDocument, val conflict: Boolean)

internal data class CopyState(
    val source: CopyDocument? = null,
    val destination: CopyDocument? = null,
    val entries: List<CopyDocument> = emptyList(),
    val review: CopyReview? = null,
    val status: CopyStatus? = null,
    val busy: Boolean = false,
    val copying: Boolean = false,
    val bytes: Long = 0L,
)

internal class CopyViewModel(
    private val storage: SafCopyStorage,
    private val journal: TransferJournalDao,
) : ViewModel() {
    private val engine = CopyEngine(storage)
    private val mutableState = MutableStateFlow(CopyState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private val recovery = viewModelScope.async { journal.interruptRunning() }
    val history = journal.observe()

    fun selectFolder(uri: String, destination: Boolean) = loadFolders {
        val document = storage.root(uri)
        if (destination) copy(destination = document) else copy(source = document, entries = listing(document))
    }

    fun swap() = loadFolders {
        val nextSource = destination ?: return@loadFolders this
        copy(source = nextSource, destination = source, entries = listing(nextSource))
    }

    fun refresh() = loadFolders {
        val folder = source ?: return@loadFolders this
        copy(entries = listing(folder))
    }

    private fun listing(folder: CopyDocument): List<CopyDocument> =
        storage.children(folder).distinctBy { it.uri }.sortedWith(
            compareBy<CopyDocument> { !it.isFolder }.thenBy { nameKey(it.name) }.thenBy { it.documentId },
        )

    private fun loadFolders(load: CopyState.() -> CopyState) {
        val current = state.value
        if (current.busy || current.review != null) return
        mutableState.value = current.copy(busy = true, status = null)
        operation = viewModelScope.launch {
            try {
                mutableState.value = withContext(Dispatchers.IO) { load(current) }
                    .copy(busy = false, status = null)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutableState.update { it.copy(busy = false, status = CopyStatus.FOLDER_FAILED) }
            }
        }
    }

    fun review(document: CopyDocument) {
        val current = state.value
        val target = current.destination ?: return
        if (document !in current.entries || !target.canCreate) return
        loadFolders { copy(review = CopyReview(document, engine.conflict(document, target))) }
    }

    fun dismissReview() {
        mutableState.update { it.copy(review = null) }
    }

    fun pickerUnavailable() {
        mutableState.update { it.copy(status = CopyStatus.PICKER_UNAVAILABLE) }
    }

    fun copy(policy: CopyPolicy) {
        val current = state.value
        val review = current.review ?: return
        val target = current.destination ?: return
        if (current.busy) return
        if (policy == CopyPolicy.SKIP) {
            mutableState.update { it.copy(review = null, status = CopyStatus.SKIPPED) }
            return
        }

        mutableState.value = current.copy(review = null, busy = true, copying = true, status = null, bytes = 0)
        val entry = TransferJournalEntry(
            id = UUID.randomUUID().toString(),
            sourceUri = review.document.uri,
            destinationUri = target.uri,
            sourceName = review.document.name,
            destinationName = target.name,
            policy = policy.name,
            createdAt = System.currentTimeMillis(),
        )
        operation = viewModelScope.launch {
            var started = false
            var bytesCopied = 0L
            var outcome = CopyStatus.FAILED
            var conflict = false
            try {
                recovery.await()
                withContext(NonCancellable) {
                    journal.insert(entry)
                    started = true
                }
                val result = withContext(Dispatchers.IO) {
                    var lastUpdate = 0L
                    engine.copy(review.document, target, policy) { bytes ->
                        bytesCopied = bytes
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 150) {
                            lastUpdate = now
                            mutableState.update { it.copy(bytes = bytes) }
                        }
                    }
                }
                outcome = if (result == CopyResult.COPIED) CopyStatus.COPIED else CopyStatus.SKIPPED
            } catch (failure: CopyException) {
                conflict = failure.problem == CopyProblem.CONFLICT
                outcome = when (failure.problem) {
                    CopyProblem.OVERLAP -> CopyStatus.OVERLAP
                    CopyProblem.UNSUPPORTED -> CopyStatus.UNSUPPORTED
                    CopyProblem.UNVERIFIED -> CopyStatus.UNVERIFIED
                    CopyProblem.CLEANUP -> CopyStatus.CLEANUP
                    CopyProblem.CONFLICT -> CopyStatus.FAILED
                }
            } catch (failure: CancellationException) {
                outcome = CopyStatus.CANCELLED
                throw failure
            } catch (_: Exception) {
                outcome = if (started) CopyStatus.FAILED else CopyStatus.JOURNAL_FAILED
            } finally {
                if (started) {
                    val persisted = withContext(NonCancellable) {
                        runCatching { journal.finish(entry.id, outcome.name, bytesCopied) == 1 }
                            .getOrDefault(false)
                    }
                    if (!persisted) outcome = CopyStatus.JOURNAL_FAILED
                }
                val showConflict = conflict && outcome != CopyStatus.JOURNAL_FAILED
                mutableState.update {
                    it.copy(
                        busy = false,
                        copying = false,
                        bytes = bytesCopied,
                        status = if (showConflict) null else outcome,
                        review = if (showConflict) review.copy(conflict = true) else null,
                    )
                }
            }
        }
    }

    fun cancel() {
        if (state.value.copying) operation?.cancel()
    }
}
