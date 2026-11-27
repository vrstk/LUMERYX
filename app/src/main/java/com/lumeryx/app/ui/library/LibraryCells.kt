package com.lumeryx.app.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.lumeryx.app.R
import com.lumeryx.app.library.LibraryFolder
import com.lumeryx.app.library.MediaKind
import com.lumeryx.app.library.mediaKind
import com.lumeryx.app.model.MediaItem

@Composable
internal fun FolderCell(folder: LibraryFolder, listLayout: Boolean, onClick: () -> Unit) {
    LibraryCell(
        title = folder.name,
        listLayout = listLayout,
        onClick = onClick,
        preview = {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
    )
}

@Composable
internal fun MediaCell(
    item: MediaItem,
    listLayout: Boolean,
    favorite: Boolean,
    favoriteEnabled: Boolean,
    onFavorite: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    LibraryCell(
        title = item.displayName,
        listLayout = listLayout,
        onClick = onOpen,
        preview = { MediaThumbnail(item) },
        action = {
            FavoriteButton(favorite, favoriteEnabled) { onFavorite(!favorite) }
        },
    )
}

@Composable
private fun LibraryCell(
    title: String,
    listLayout: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Card(onClick = onClick) {
        if (listLayout) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(80.dp).height(68.dp)) { preview() }
                CellTitle(title, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
                action?.invoke()
            }
        } else {
            Box(Modifier.fillMaxWidth().height(124.dp)) { preview() }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CellTitle(title, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
                action?.invoke()
            }
        }
    }
}

@Composable
private fun CellTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun FavoriteButton(favorite: Boolean, enabled: Boolean, onClick: () -> Unit) {
    LibraryIconButton(
        icon = if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
        label = if (favorite) R.string.library_remove_favorite else R.string.library_add_favorite,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun MediaThumbnail(item: MediaItem) {
    val context = LocalContext.current
    val isVideo = mediaKind(item.mimeType, item.displayName) == MediaKind.VIDEO
    var failed by remember(item) { mutableStateOf(false) }
    val request = remember(context, item, isVideo) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.location.uri))
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCacheKeyExtra("modifiedAt", item.modifiedAt?.toEpochMilli()?.toString().orEmpty())
            .apply {
                if (isVideo) decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            }
            .build()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onError = { failed = true },
            onSuccess = { failed = false },
        )
        if (isVideo || failed) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(
                    painterResource(if (isVideo) R.drawable.ic_play else R.drawable.ic_library),
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp).size(20.dp),
                )
            }
        }
    }
}
