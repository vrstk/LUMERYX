package com.lumeryx.app.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class BrowserTab(
    val id: Long,
    val url: String? = null,
    val label: String = "",
    val revision: Int = 0,
)

internal data class BrowserLink(val url: String, val label: String)

internal data class IncomingLink(val url: String?)

internal data class BrowserSession(
    val tabs: List<BrowserTab> = listOf(BrowserTab(1)),
    val selected: Long = 1,
    val bookmarks: List<BrowserLink> = emptyList(),
    val history: List<BrowserLink> = emptyList(),
) {
    val current: BrowserTab get() = tabs.first { it.id == selected }
}

internal class BrowserModel(context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(restore())
    val state = mutable.asStateFlow()
    private val pending = MutableStateFlow<IncomingLink?>(null)
    val incoming = pending.asStateFlow()
    private var nextId = state.value.tabs.maxOf { it.id } + 1

    fun offerLink(text: String) {
        pending.value = IncomingLink(WebAddress.approved(text.trim()))
    }

    fun resolveLink(accept: Boolean) {
        val url = pending.value?.url
        pending.value = null
        if (accept && url != null) navigate(url)
    }

    fun addTab() {
        val session = state.value
        if (session.tabs.size >= MAX_TABS) return
        val tab = BrowserTab(nextId++)
        save(session.copy(tabs = session.tabs + tab, selected = tab.id))
    }

    fun select(id: Long) {
        val session = state.value
        if (session.tabs.any { it.id == id }) save(session.copy(selected = id))
    }

    fun close(id: Long) {
        val session = state.value
        val remaining = session.tabs.filterNot { it.id == id }
            .ifEmpty { listOf(BrowserTab(nextId++)) }
        val selected = if (session.selected == id) remaining.last().id else session.selected
        save(session.copy(tabs = remaining, selected = selected))
    }

    fun navigate(input: String): Boolean {
        val url = WebAddress.fromInput(input) ?: return false
        val current = state.value.current
        updateTab(current.copy(url = url, label = "", revision = current.revision + 1))
        return true
    }

    fun pageLoaded(tab: BrowserTab, address: String, title: String?) {
        val url = WebAddress.approved(address) ?: return
        val session = state.value
        val current = session.current
        if (current.id != tab.id || current.revision != tab.revision) return
        val link = BrowserLink(url, title.orEmpty().take(150))
        val history = (listOf(link) + session.history.filterNot { it.url == url }).take(100)
        updateTab(current.copy(url = url, label = link.label), history)
    }

    fun toggleBookmark() {
        val session = state.value
        val tab = session.current
        val url = tab.url ?: return
        val remaining = session.bookmarks.filterNot { it.url == url }
        val bookmarks = if (remaining.size != session.bookmarks.size) remaining
        else (listOf(BrowserLink(url, tab.label)) + remaining).take(200)
        save(session.copy(bookmarks = bookmarks))
    }

    fun clearHistory() = save(state.value.copy(history = emptyList()))

    private fun updateTab(tab: BrowserTab, history: List<BrowserLink> = state.value.history) {
        val session = state.value
        save(session.copy(tabs = session.tabs.map { if (it.id == tab.id) tab else it }, history = history))
    }

    private fun save(session: BrowserSession) {
        if (session == mutable.value) return
        mutable.value = session
        val json = JSONObject()
            .put("tabs", JSONArray().apply {
                session.tabs.forEach { put(JSONObject().put("id", it.id).put("url", it.url).put("label", it.label)) }
            })
            .put("selected", session.selected)
            .put("bookmarks", encodeLinks(session.bookmarks))
            .put("history", encodeLinks(session.history))
        prefs.edit().putString("session", json.toString()).apply()
    }

    private fun restore(): BrowserSession = runCatching {
        val json = JSONObject(prefs.getString("session", "{}") ?: "{}")
        val tabs = json.optJSONArray("tabs")?.objects(MAX_TABS)?.mapNotNull { tab ->
            val id = tab.optLong("id")
            if (id !in 1 until Long.MAX_VALUE) null
            else BrowserTab(id, WebAddress.approved(tab.optString("url")), tab.optString("label").take(150))
        }?.distinctBy { it.id }.orEmpty().ifEmpty { listOf(BrowserTab(1)) }
        val selected = json.optLong("selected")
        BrowserSession(
            tabs,
            selected.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id,
            decodeLinks(json.optJSONArray("bookmarks"), 200),
            decodeLinks(json.optJSONArray("history"), 100),
        )
    }.getOrDefault(BrowserSession())

    private fun JSONArray.objects(limit: Int): List<JSONObject> =
        (0 until minOf(limit, length())).mapNotNull(::optJSONObject)

    private fun decodeLinks(array: JSONArray?, limit: Int) = array?.objects(limit)?.mapNotNull {
        val url = WebAddress.approved(it.optString("url")) ?: return@mapNotNull null
        BrowserLink(url, it.optString("label").take(150))
    }.orEmpty()

    private fun encodeLinks(links: List<BrowserLink>) = JSONArray().apply {
        links.forEach { put(JSONObject().put("url", it.url).put("label", it.label)) }
    }

    companion object {
        const val MAX_TABS = 12
    }
}
