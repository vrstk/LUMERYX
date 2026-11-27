package com.lumeryx.app.ui.library

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumeryx.app.R
import com.lumeryx.app.library.LibraryFeedback
import com.lumeryx.app.library.LibrarySort
import com.lumeryx.app.library.LibraryStatus
import com.lumeryx.app.library.LibraryViewModel
import com.lumeryx.app.library.sortMedia
import com.lumeryx.app.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryScreen(model: LibraryViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var listLayout by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.NAME) }
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            favoritesOnly = false
            model.selectRoot(uri)
        }
    }
    val chooseFolder = {
        try {
            picker.launch(null)
        } catch (_: ActivityNotFoundException) {
            model.pickerUnavailable()
        }
    }

    val feedback = state.feedback
    val feedbackText = feedback?.let {
        stringResource(
            when (it) {
                LibraryFeedback.PICKER_UNAVAILABLE -> R.string.library_picker_unavailable
                LibraryFeedback.SELECTION_FAILED -> R.string.library_selection_failed
                LibraryFeedback.FAVORITES_FAILED -> R.string.library_favorites_failed
            },
        )
    }
    LaunchedEffect(feedback, feedbackText) {
        if (feedback != null && feedbackText != null) {
            snackbar.showSnackbar(feedbackText)
            model.clearFeedback(feedback)
        }
    }

    BackHandler(
        enabled = !state.selecting && state.viewer == null && (favoritesOnly || state.path.size > 1),
    ) {
        if (favoritesOnly) favoritesOnly = false else model.parent()
    }

    val favoriteIds = remember(state.favorites) { state.favorites.mapTo(mutableSetOf()) { it.id } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.path.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.path.size > 1 && !favoritesOnly) {
                        LibraryIconButton(R.drawable.ic_back, R.string.library_up, !state.selecting, model::parent)
                    }
                    Text(
                        text = state.path.last().name,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LibraryIconButton(
                        R.drawable.ic_folder, R.string.library_choose_folder, !state.selecting, chooseFolder,
                    )
                    LibraryIconButton(
                        R.drawable.ic_refresh, R.string.library_refresh, !state.selecting, model::refresh,
                    )
                }
            }

            when {
                state.selecting || state.status == LibraryStatus.LOADING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.status == LibraryStatus.NO_ROOT -> LibraryMessage(
                    R.string.library_no_folder,
                    R.string.library_choose_folder,
                    chooseFolder,
                )
                state.status == LibraryStatus.ACCESS_DENIED -> LibraryMessage(
                    R.string.library_access_revoked,
                    R.string.library_choose_folder,
                    chooseFolder,
                )
                state.status == LibraryStatus.UNAVAILABLE -> LibraryMessage(
                    R.string.library_unavailable_error,
                    R.string.retry,
                    model::refresh,
                    secondaryLabel = R.string.library_choose_folder,
                    secondaryAction = chooseFolder,
                )
                else -> {
                    LibraryControls(
                        favoritesOnly = favoritesOnly,
                        onFavoritesOnly = { favoritesOnly = it },
                        listLayout = listLayout,
                        onToggleLayout = { listLayout = !listLayout },
                        sort = sort,
                        onSort = { sort = it },
                    )
                    val source = if (favoritesOnly) state.favorites else state.contents.media
                    var media by remember(source, sort) { mutableStateOf<List<MediaItem>?>(null) }
                    LaunchedEffect(source, sort) {
                        media = withContext(Dispatchers.Default) { sortMedia(source, sort) }
                    }
                    val folders = if (favoritesOnly) emptyList() else state.contents.folders
                    key(state.path.last().id, favoritesOnly, listLayout) {
                        LazyVerticalGrid(
                            columns = if (listLayout) GridCells.Fixed(1) else GridCells.Adaptive(144.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(folders, key = { "folder:${it.id}" }, contentType = { "folder" }) { folder ->
                                FolderCell(folder, listLayout) { model.openFolder(folder) }
                            }
                            val displayed = media
                            if (displayed == null) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else {
                                if (folders.isEmpty() && displayed.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            stringResource(
                                                if (favoritesOnly) R.string.library_empty_favorites
                                                else R.string.library_empty_folder,
                                            ),
                                            Modifier.padding(12.dp),
                                        )
                                    }
                                }
                                items(displayed.size, key = { "media:${displayed[it].id}" }) { index ->
                                    val item = displayed[index]
                                    MediaCell(
                                        item = item,
                                        listLayout = listLayout,
                                        favorite = item.id in favoriteIds,
                                        favoriteEnabled = item.id !in state.pendingFavoriteIds,
                                        onFavorite = { model.setFavorite(item, it) },
                                        onOpen = { model.openViewer(displayed, index) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.viewer == null) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.viewer?.let { viewer ->
        ViewerDialog(
            session = viewer,
            favoriteIds = favoriteIds,
            pendingFavoriteIds = state.pendingFavoriteIds,
            snackbar = snackbar,
            onFavorite = model::setFavorite,
            onClose = model::closeViewer,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryControls(
    favoritesOnly: Boolean,
    onFavoritesOnly: (Boolean) -> Unit,
    listLayout: Boolean,
    onToggleLayout: () -> Unit,
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = !favoritesOnly,
                onClick = { onFavoritesOnly(false) },
                label = { Text(stringResource(R.string.library_all_items), maxLines = 1) },
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = { onFavoritesOnly(true) },
                label = {
                    Text(
                        stringResource(R.string.library_favorites),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LibraryIconButton(
                if (listLayout) R.drawable.ic_grid else R.drawable.ic_list,
                if (listLayout) R.string.library_grid else R.string.library_list,
                onClick = onToggleLayout,
            )
            var expanded by remember { mutableStateOf(false) }
            Box {
                LibraryIconButton(R.drawable.ic_sort, sort.description, onClick = { expanded = true })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label)) },
                            leadingIcon = if (sort == option) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                expanded = false
                                onSort(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

private val LibrarySort.label: Int
    get() = when (this) {
        LibrarySort.NAME -> R.string.library_sort_name
        LibrarySort.DATE -> R.string.library_sort_date
        LibrarySort.SIZE -> R.string.library_sort_size
    }

private val LibrarySort.description: Int
    get() = when (this) {
        LibrarySort.NAME -> R.string.library_sort_current_name
        LibrarySort.DATE -> R.string.library_sort_current_date
        LibrarySort.SIZE -> R.string.library_sort_current_size
    }

@Composable
private fun LibraryMessage(
    @StringRes message: Int,
    @StringRes actionLabel: Int,
    action: () -> Unit,
    @StringRes secondaryLabel: Int? = null,
    secondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = action) { Text(stringResource(actionLabel)) }
        if (secondaryLabel != null && secondaryAction != null) {
            androidx.compose.material3.TextButton(onClick = secondaryAction) {
                Text(stringResource(secondaryLabel))
            }
        }
    }
}

@Composable
internal fun LibraryIconButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}
