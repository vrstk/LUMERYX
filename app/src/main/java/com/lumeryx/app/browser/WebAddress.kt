package com.lumeryx.app.browser

import java.net.URI

internal object WebAddress {
    fun fromInput(input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || value.length > 4096) return null
        val candidate = if (":" !in value.substringBefore('/') && !value.startsWith('/')) {
            "https://$value"
        } else {
            value
        }
        return approved(candidate)
    }

    fun approved(value: String): String? {
        if (value.length > 4096 || value.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return null
        if (host.isBlank() || uri.port !in -1..65535 || uri.port == 0) return null
        return uri.toASCIIString()
    }
}
