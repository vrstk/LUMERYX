package com.lumeryx.app.ui.transfer

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumeryx.app.R
import com.lumeryx.app.transfer.CopyPolicy
import com.lumeryx.app.transfer.CopyStatus
import com.lumeryx.app.transfer.CopyViewModel
import com.lumeryx.app.ui.library.LibraryIconButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CopyScreen(model: CopyViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var destinationPicker by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { model.selectFolder(it.toString(), destinationPicker) }
    }
    val select = { destination: Boolean ->
        destinationPicker = destination
        try {
            picker.launch(null)
        } catch (_: ActivityNotFoundException) {
            model.pickerUnavailable()
        }
    }
    val source = state.source
    val destination = state.destination
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.copy_heading), style = MaterialTheme.typography.titleLarge)
        }
        item {
            Text(stringResource(R.string.copy_choose_help), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { select(false) }, enabled = !state.busy) {
                    Text(stringResource(R.string.copy_choose_source))
                }
                OutlinedButton(onClick = { select(true) }, enabled = !state.busy) {
                    Text(stringResource(R.string.copy_choose_destination))
                }
            }
        }
        if (source != null) {
            item {
                Text(stringResource(R.string.copy_source, source.name))
            }
        }
        if (destination != null) {
            item {
                Text(stringResource(R.string.copy_destination, destination.name))
                if (!destination.canCreate) {
                    Text(
                        stringResource(R.string.copy_destination_read_only),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (source != null && destination != null) {
            item {
                TextButton(onClick = model::swap, enabled = !state.busy) {
                    Text(stringResource(R.string.copy_swap))
                }
            }
        }
        if (state.busy) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.copying) {
                    Text(
                        stringResource(R.string.copy_progress, Formatter.formatFileSize(context, state.bytes)),
                        Modifier.padding(top = 6.dp),
                    )
                    TextButton(onClick = model::cancel) { Text(stringResource(R.string.copy_cancel)) }
                }
            }
        }
        state.status?.let { status ->
            item {
                Text(
                    stringResource(status.label),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (source != null) {
            item {
                FlowRow(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.copy_source_contents),
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LibraryIconButton(
                        R.drawable.ic_refresh, R.string.library_refresh, !state.busy, model::refresh,
                    )
                }
                if (state.entries.isEmpty() && !state.busy) {
                    Text(stringResource(R.string.copy_source_empty))
                }
            }
        }
        items(state.entries, key = { it.uri }) { document ->
            Card(
                onClick = { model.review(document) },
                enabled = !state.busy && destination?.canCreate == true,
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painterResource(if (document.isFolder) R.drawable.ic_folder else R.drawable.ic_library),
                        contentDescription = null,
                    )
                    Text(
                        document.name,
                        Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    state.review?.let { review ->
        AlertDialog(
            onDismissRequest = model::dismissReview,
            title = { Text(stringResource(if (review.conflict) R.string.copy_conflict else R.string.copy_confirm)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.copy_confirm_destination, review.document.name, destination?.name.orEmpty()))
                    if (review.conflict) Text(stringResource(R.string.copy_conflict_help))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    model.copy(if (review.conflict) CopyPolicy.KEEP_BOTH else CopyPolicy.ASK)
                }) {
                    Text(stringResource(if (review.conflict) R.string.copy_keep_both else R.string.copy_confirm))
                }
            },
            dismissButton = {
                if (review.conflict) {
                    TextButton(onClick = { model.copy(CopyPolicy.SKIP) }) {
                        Text(stringResource(R.string.copy_skip))
                    }
                } else {
                    TextButton(onClick = model::dismissReview) { Text(stringResource(R.string.copy_cancel)) }
                }
            },
        )
    }
}

private val CopyStatus.label: Int
    get() = when (this) {
        CopyStatus.PICKER_UNAVAILABLE -> R.string.library_picker_unavailable
        CopyStatus.FOLDER_FAILED -> R.string.copy_folder_failed
        CopyStatus.COPIED -> R.string.copy_done
        CopyStatus.SKIPPED -> R.string.copy_skipped
        CopyStatus.FAILED -> R.string.copy_failed
        CopyStatus.CANCELLED -> R.string.copy_cancelled
        CopyStatus.OVERLAP -> R.string.copy_overlap
        CopyStatus.UNSUPPORTED -> R.string.copy_unsupported
        CopyStatus.UNVERIFIED -> R.string.copy_unverified
        CopyStatus.CLEANUP -> R.string.copy_cleanup
    }
