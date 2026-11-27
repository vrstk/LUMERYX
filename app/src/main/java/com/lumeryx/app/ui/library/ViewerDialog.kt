package com.lumeryx.app.ui.library

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.lumeryx.app.R
import com.lumeryx.app.library.MediaKind
import com.lumeryx.app.library.ViewerSession
import com.lumeryx.app.library.mediaKind
import com.lumeryx.app.model.MediaItem
import com.lumeryx.app.ui.LumeryxTheme

@Composable
internal fun ViewerDialog(
    session: ViewerSession,
    favoriteIds: Set<String>,
    pendingFavoriteIds: Set<String>,
    snackbar: SnackbarHostState,
    onFavorite: (MediaItem, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }

        LumeryxTheme(dark = true) {
            val pager = rememberPagerState(initialPage = session.initialIndex) { session.items.size }
            val current = session.items[pager.currentPage]
            val favorite = current.id in favoriteIds
            Box(
                Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryIconButton(R.drawable.ic_back, R.string.library_close_viewer, onClick = onClose)
                        Text(
                            text = current.displayName,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        FavoriteButton(favorite, current.id !in pendingFavoriteIds) {
                            onFavorite(current, !favorite)
                        }
                    }

                    HorizontalPager(
                        state = pager,
                        key = { session.items[it].id },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { index ->
                        val item = session.items[index]
                        if (mediaKind(item.mimeType, item.displayName) == MediaKind.VIDEO) {
                            VideoPage(item, active = pager.settledPage == index && pager.currentPage == index)
                        } else {
                            PhotoPage(item)
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun PhotoPage(item: MediaItem) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val maxX = bounds.width * (scale - 1f) / 2f
        val maxY = bounds.height * (scale - 1f) / 2f
        offset = Offset(
            (offset.x + pan.x).coerceIn(-maxX, maxX),
            (offset.y + pan.y).coerceIn(-maxY, maxY),
        )
    }
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember(attempt) { mutableStateOf(false) }
    val request = remember(context, item, attempt) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.location.uri))
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCacheKeyExtra("modifiedAt", item.modifiedAt?.toEpochMilli()?.toString().orEmpty())
            .build()
    }
    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { bounds = it }
            .transformable(state = transform, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        key(attempt) {
            AsyncImage(
                model = request,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        }
        if (failed) PlaybackMessage(R.string.library_photo_failed) { attempt++ }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPage(item: MediaItem, active: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var position by rememberSaveable { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    DisposableEffect(context, lifecycle, item, active, attempt) {
        var ownedPlayer: ExoPlayer? = null
        fun release() {
            ownedPlayer?.let {
                position = it.currentPosition
                it.release()
            }
            ownedPlayer = null
            player = null
            playing = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (active && ownedPlayer == null) {
                    failed = false
                    val next = ExoPlayer.Builder(context).build()
                    ownedPlayer = next
                    next.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            failed = true
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            playing = isPlaying
                        }
                    })
                    next.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.location.uri), position)
                    next.prepare()
                    player = next
                }
                Lifecycle.Event.ON_PAUSE -> release()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val currentPlayer = player
        if (currentPlayer == null) {
            MediaThumbnail(item)
        } else {
            key(currentPlayer) {
                AndroidView(
                    factory = { viewContext -> PlayerView(viewContext) },
                    update = {
                        it.player = currentPlayer
                        it.keepScreenOn = playing
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (active && failed) {
            PlaybackMessage(R.string.library_video_failed) {
                position = 0L
                attempt++
            }
        }
    }
}

@Composable
private fun PlaybackMessage(@StringRes message: Int, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.background(Color.Black.copy(alpha = 0.88f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(message), color = Color.White)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
