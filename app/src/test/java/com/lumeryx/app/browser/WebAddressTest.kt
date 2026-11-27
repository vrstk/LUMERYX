package com.lumeryx.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebAddressTest {
    @Test
    fun acceptsHttpsAndAddsSchemeToPlainAddresses() {
        assertEquals("https://example.org/page", WebAddress.fromInput(" example.org/page "))
        assertEquals("https://example.org:8443/p?q=1", WebAddress.approved("https://example.org:8443/p?q=1"))
    }

    @Test
    fun rejectsNonWebAndNonTlsAddresses() {
        listOf("http://example.org", "file:///private", "content://example.org/a",
            "javascript:alert(1)", "intent://example.org", "//example.org", "about:blank").forEach {
            assertNull(it, WebAddress.fromInput(it))
        }
    }

    @Test
    fun rejectsAmbiguousAuthority() {
        listOf("https:///path", "https://trusted.example@elsewhere.example",
            "https://example.org\\@other.example", "https://example.org:99999",
            "https://exa mple.org", "https://%65xample.org").forEach {
            assertNull(it, WebAddress.approved(it))
        }
    }
}
