package com.lumeryx.app.ui.transfer

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumeryx.app.R
import com.lumeryx.app.transfer.CopyViewModel

@Composable
internal fun TransferHistoryScreen(model: CopyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by model.history.collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.transfer_history_back)) }
            Text(stringResource(R.string.transfer_history_help))
            if (entries.isEmpty()) Text(stringResource(R.string.transfer_history_empty))
        }
        items(entries, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.sourceName, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.copy_destination, entry.destinationName))
                    val label = when (entry.status) {
                        "RUNNING" -> R.string.transfer_running
                        "INTERRUPTED" -> R.string.transfer_interrupted
                        "COPIED" -> R.string.copy_done
                        "SKIPPED" -> R.string.copy_skipped
                        "CANCELLED" -> R.string.copy_cancelled
                        "OVERLAP" -> R.string.copy_overlap
                        "UNSUPPORTED" -> R.string.copy_unsupported
                        "UNVERIFIED" -> R.string.copy_unverified
                        "CLEANUP" -> R.string.copy_cleanup
                        else -> R.string.copy_failed
                    }
                    Text(stringResource(label))
                    if (entry.status != "RUNNING" && entry.status != "INTERRUPTED") {
                        Text(stringResource(R.string.copy_progress, Formatter.formatFileSize(context, entry.bytes)))
                    }
                }
            }
        }
    }
}
