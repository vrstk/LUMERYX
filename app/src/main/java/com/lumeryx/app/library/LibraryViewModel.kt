package com.lumeryx.app.library

import android.database.SQLException
import android.net.Uri
import android.os.OperationCanceledException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumeryx.app.model.MediaItem
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class LibraryStatus { LOADING, NO_ROOT, READY, ACCESS_DENIED, UNAVAILABLE }

internal enum class LibraryFeedback { PICKER_UNAVAILABLE, SELECTION_FAILED, FAVORITES_FAILED }

internal data class ViewerSession(val items: List<MediaItem>, val initialIndex: Int)

internal data class LibraryState(
    val status: LibraryStatus = LibraryStatus.LOADING,
    val path: List<LibraryFolder> = emptyList(),
    val contents: FolderContents = FolderContents(emptyList(), emptyList()),
    val favorites: List<MediaItem> = emptyList(),
    val pendingFavoriteIds: Set<String> = emptySet(),
    val selecting: Boolean = false,
    val feedback: LibraryFeedback? = null,
    val viewer: ViewerSession? = null,
)

internal class LibraryViewModel(private val repository: SafLibraryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()

    private var root: Uri? = null
    private var rootGeneration = 0L
    private var listing: Job? = null
    private var observingFavorites: Job? = null
    private var storageRead: Job? = null

    init {
        restoreRoot()
    }

    private fun restoreRoot() {
        storageRead?.cancel()
        mutableState.update { it.copy(status = LibraryStatus.LOADING) }
        storageRead = viewModelScope.launch {
            try {
                val tree = repository.selectedRoot()
                if (tree == null) {
                    mutableState.update { it.copy(status = LibraryStatus.NO_ROOT) }
                } else {
                    displayRoot(tree)
                }
            } catch (_: SQLException) {
                mutableState.update { it.copy(status = LibraryStatus.UNAVAILABLE) }
            }
        }
    }

    fun selectRoot(tree: Uri) {
        if (state.value.selecting) return
        storageRead?.cancel()
        mutableState.update { it.copy(selecting = true, feedback = null) }
        viewModelScope.launch {
            try {
                repository.selectRoot(tree)
                displayRoot(tree)
            } catch (_: SecurityException) {
                rejectedRoot()
            } catch (_: IllegalArgumentException) {
                rejectedRoot()
            } catch (_: SQLException) {
                rejectedRoot()
            }
        }
    }

    private fun displayRoot(tree: Uri) {
        listing?.cancel()
        observingFavorites?.cancel()
        root = tree
        rootGeneration++
        mutableState.value = LibraryState()
        observingFavorites = viewModelScope.launch {
            repository.favorites(tree)
                .catch { error ->
                    if (error !is SQLException) throw error
                    mutableState.update { it.copy(feedback = LibraryFeedback.FAVORITES_FAILED) }
                }
                .collect { favorites -> mutableState.update { it.copy(favorites = favorites) } }
        }
        showFolder(emptyList())
    }

    private fun rejectedRoot() {
        mutableState.update {
            it.copy(
                selecting = false,
                status = if (root == null) LibraryStatus.NO_ROOT else it.status,
                feedback = LibraryFeedback.SELECTION_FAILED,
            )
        }
    }

    fun refresh() {
        if (state.value.selecting) return
        if (root == null) restoreRoot() else showFolder(state.value.path)
    }

    fun openFolder(folder: LibraryFolder) {
        val current = state.value
        if (current.selecting || current.status != LibraryStatus.READY) return
        if (folder !in current.contents.folders || current.path.any { it.id == folder.id }) return
        showFolder(current.path + folder)
    }

    fun parent() {
        val current = state.value
        if (!current.selecting && current.path.size > 1) showFolder(current.path.dropLast(1))
    }

    private fun showFolder(requestedPath: List<LibraryFolder>) {
        val tree = root ?: return
        listing?.cancel()
        mutableState.update {
            it.copy(status = LibraryStatus.LOADING, path = requestedPath, contents = emptyContents)
        }
        listing = viewModelScope.launch {
            try {
                val path = requestedPath.ifEmpty { listOf(repository.readRoot(tree)) }
                mutableState.update { it.copy(path = path) }
                val contents = repository.readFolder(tree, path.last().id)
                mutableState.update { it.copy(status = LibraryStatus.READY, contents = contents) }
            } catch (_: SecurityException) {
                mutableState.update { it.copy(status = LibraryStatus.ACCESS_DENIED) }
            } catch (_: IOException) {
                mutableState.update { it.copy(status = LibraryStatus.UNAVAILABLE) }
            } catch (_: IllegalArgumentException) {
                mutableState.update { it.copy(status = LibraryStatus.UNAVAILABLE) }
            } catch (_: OperationCanceledException) {
                mutableState.update { it.copy(status = LibraryStatus.UNAVAILABLE) }
            }
        }
    }

    fun openViewer(items: List<MediaItem>, index: Int) {
        if (state.value.status != LibraryStatus.READY || state.value.selecting) return
        if (index !in items.indices) return
        mutableState.update { it.copy(viewer = ViewerSession(items, index)) }
    }

    fun closeViewer() {
        mutableState.update { it.copy(viewer = null) }
    }

    fun setFavorite(media: MediaItem, favorite: Boolean) {
        val tree = root ?: return
        val generation = rootGeneration
        val current = state.value
        if (current.selecting || media.id in current.pendingFavoriteIds) return
        mutableState.update { it.copy(pendingFavoriteIds = it.pendingFavoriteIds + media.id) }

        viewModelScope.launch {
            try {
                repository.setFavorite(tree, media, favorite)
                if (generation == rootGeneration) {
                    mutableState.update { state ->
                        val favorites = state.favorites.filterNot { it.id == media.id }
                        state.copy(favorites = if (favorite) favorites + media else favorites)
                    }
                }
            } catch (_: SQLException) {
                if (generation == rootGeneration) mutableState.update { it.copy(feedback = LibraryFeedback.FAVORITES_FAILED) }
            } finally {
                if (generation == rootGeneration) {
                    mutableState.update { it.copy(pendingFavoriteIds = it.pendingFavoriteIds - media.id) }
                }
            }
        }
    }

    fun pickerUnavailable() {
        mutableState.update { it.copy(feedback = LibraryFeedback.PICKER_UNAVAILABLE) }
    }

    fun clearFeedback(feedback: LibraryFeedback) {
        mutableState.update { if (it.feedback == feedback) it.copy(feedback = null) else it }
    }

    private companion object {
        val emptyContents = FolderContents(emptyList(), emptyList())
    }
}
