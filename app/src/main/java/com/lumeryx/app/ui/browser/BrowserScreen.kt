package com.lumeryx.app.ui.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumeryx.app.R
import com.lumeryx.app.browser.BrowserLink
import com.lumeryx.app.browser.BrowserModel
import com.lumeryx.app.browser.BrowserTab
import com.lumeryx.app.browser.WebAddress
import java.io.ByteArrayInputStream

private enum class BrowserPanel { TABS, BOOKMARKS, HISTORY }

@Composable
internal fun BrowserScreen(model: BrowserModel) {
    val session by model.state.collectAsStateWithLifecycle()
    val incoming by model.incoming.collectAsStateWithLifecycle()
    val tab = session.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var address by rememberSaveable(tab.id, tab.url) { mutableStateOf(tab.url.orEmpty()) }
    var badInput by remember(tab.id, tab.revision) { mutableStateOf(false) }
    var webError by remember(tab.id, tab.revision) { mutableStateOf<Int?>(null) }
    var rendererGone by remember(tab.id, tab.revision) { mutableStateOf(false) }
    var active by remember(tab.id, tab.revision) { mutableStateOf<WebView?>(null) }
    var canBack by remember(tab.id, tab.revision) { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<BrowserPanel?>(null) }
    var linkMenu by remember { mutableStateOf(false) }
    val supported = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }

    LaunchedEffect(incoming) { if (incoming != null) panel = null }

    val go = {
        badInput = !model.navigate(address)
        if (!badInput) focus.clearFocus()
    }
    BackHandler(enabled = canBack && !rendererGone) { active?.goBack() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.take(4096); badInput = false },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            label = { Text(stringResource(R.string.browser_url)) },
            trailingIcon = {
                TextButton(onClick = go) { Text(stringResource(R.string.browser_go)) }
            },
            singleLine = true,
            isError = badInput,
            supportingText = if (badInput) {
                { Text(stringResource(R.string.browser_invalid_url)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
        )
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            if (canBack && !rendererGone) {
                TextButton(onClick = { active?.goBack() }) { Text(stringResource(R.string.browser_back)) }
            }
            TextButton(onClick = { panel = BrowserPanel.TABS }) {
                Text(stringResource(R.string.browser_tabs_count, session.tabs.size))
            }
            TextButton(onClick = { panel = BrowserPanel.BOOKMARKS }) {
                Text(stringResource(R.string.browser_bookmarks))
            }
            TextButton(onClick = { panel = BrowserPanel.HISTORY }) {
                Text(stringResource(R.string.browser_history))
            }
            if (tab.url != null) {
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { linkMenu = true }) {
                        Text(stringResource(R.string.browser_link))
                    }
                    DropdownMenu(expanded = linkMenu, onDismissRequest = { linkMenu = false }) {
                        val bookmarked = session.bookmarks.any { it.url == tab.url }
                        DropdownMenuItem(
                            text = { Text(stringResource(if (bookmarked) R.string.browser_unsave else R.string.browser_save)) },
                            onClick = { linkMenu = false; model.toggleBookmark() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_share)) },
                            onClick = {
                                linkMenu = false
                                val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, tab.url)
                                try {
                                    context.startActivity(Intent.createChooser(intent, null))
                                } catch (_: ActivityNotFoundException) {
                                    webError = R.string.browser_no_handler
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_external)) },
                            onClick = {
                                linkMenu = false
                                val url = WebAddress.approved(tab.url)
                                if (url != null) {
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
                                    try {
                                        context.startActivity(Intent.createChooser(intent, null))
                                    } catch (_: ActivityNotFoundException) {
                                        webError = R.string.browser_no_handler
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        val error = webError
        if (error != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(
                    stringResource(error),
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                if (tab.url != null && error in setOf(R.string.browser_load_error, R.string.browser_renderer_error)) {
                    TextButton(onClick = { model.navigate(tab.url) }) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        if (!supported) {
            Text(stringResource(R.string.browser_webview_unavailable), Modifier.padding(16.dp))
        } else if (tab.url == null) {
            Text(stringResource(R.string.browser_empty), Modifier.padding(16.dp))
        } else if (!rendererGone) {
            key(tab.id, tab.revision) {
                WebPage(
                    tab = tab,
                    onView = { active = it },
                    onLoaded = { view, uri ->
                        model.pageLoaded(tab, uri, view.title)
                        canBack = view.canGoBack()
                    },
                    onError = { webError = it },
                    onRendererGone = {
                        active = null
                        canBack = false
                        webError = R.string.browser_renderer_error
                        rendererGone = true
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    when (panel) {
        BrowserPanel.TABS -> AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.browser_tabs)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(session.tabs, key = { it.id }) { item ->
                        Row {
                            TextButton(
                                onClick = { model.select(item.id); panel = null },
                                modifier = Modifier.weight(1f),
                            ) {
                                val label = item.label.ifBlank { item.url ?: stringResource(R.string.browser_blank_tab) }
                                Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { model.close(item.id) }) { Text(stringResource(R.string.browser_close_tab)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { model.addTab(); panel = null },
                    enabled = session.tabs.size < BrowserModel.MAX_TABS,
                ) { Text(stringResource(R.string.browser_new_tab)) }
            },
            dismissButton = {
                TextButton(onClick = { panel = null }) { Text(stringResource(R.string.browser_done)) }
            },
        )
        BrowserPanel.BOOKMARKS, BrowserPanel.HISTORY -> {
            val history = panel == BrowserPanel.HISTORY
            LinkDialog(
                title = stringResource(if (history) R.string.browser_history else R.string.browser_bookmarks),
                links = if (history) session.history else session.bookmarks,
                onOpen = { model.navigate(it); panel = null },
                onClose = { panel = null },
                onClear = if (history && session.history.isNotEmpty()) model::clearHistory else null,
            )
        }
        null -> Unit
    }

    incoming?.let { link ->
        AlertDialog(
            onDismissRequest = { model.resolveLink(false) },
            title = { Text(stringResource(R.string.browser_incoming)) },
            text = { Text(link.url ?: stringResource(R.string.browser_invalid_url), maxLines = 5, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { model.resolveLink(true) }, enabled = link.url != null) {
                    Text(stringResource(R.string.browser_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { model.resolveLink(false) }) { Text(stringResource(R.string.browser_done)) }
            },
        )
    }
}

@Composable
private fun LinkDialog(
    title: String,
    links: List<BrowserLink>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onClear: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            if (links.isEmpty()) {
                Text(stringResource(R.string.browser_no_links))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(links) { link ->
                        TextButton(onClick = { onOpen(link.url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(link.label.ifBlank { link.url }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.browser_done)) } },
        dismissButton = {
            if (onClear != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.browser_clear_history)) }
            }
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPage(
    tab: BrowserTab,
    onView: (WebView) -> Unit,
    onLoaded: (WebView, String) -> Unit,
    onError: (Int?) -> Unit,
    onRendererGone: () -> Unit,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    safeBrowsingEnabled = true
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    private var failed = false

                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        failed = false
                        onError(null)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val denied = WebAddress.approved(request.url.toString()) == null
                        if (denied && request.isForMainFrame) onError(R.string.browser_invalid_url)
                        return denied
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (WebAddress.approved(request.url.toString()) != null) return null
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) {
                            failed = true
                            onError(R.string.browser_load_error)
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        if (!failed) onLoaded(view, url)
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        onRendererGone()
                        return true
                    }
                }
                WebAddress.approved(tab.url.orEmpty())?.let(::loadUrl)
                onView(this)
            }
        },
        onRelease = { it.destroy() },
    )
}
