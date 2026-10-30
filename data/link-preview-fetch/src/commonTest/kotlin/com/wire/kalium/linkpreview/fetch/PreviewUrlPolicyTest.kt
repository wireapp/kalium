/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.linkpreview.fetch

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewUrlPolicyTest {
    @Test
    fun givenRawRequestSeparators_whenValidating_thenRejectsPathAndHostnameInjection() {
        listOf('\r', '\n', '\t', ' ', '\u0000', '\u007f').forEach { separator ->
            val url = URLBuilder("https://example.com").apply { encodedPath = "/image${separator}Injected:secret" }.build()
            assertFalse(isAllowedPreviewUrl(url), separator.code.toString())
        }
        listOf('\r', '\n', '\t', ' ').forEach { separator ->
            val url = URLBuilder("https://example.com").apply { host = "example.com${separator}Injected" }.build()
            assertFalse(isAllowedPreviewUrl(url), separator.code.toString())
        }
        assertTrue(isAllowedPreviewUrl(Url("https://example.com/image%0d%0aInjected%3Asecret")))
    }

    @Test
    fun givenUnsafeUrl_whenValidating_thenRejectsSchemeCredentialsAndPrivateLiterals() {
        listOf(
            "http://example.com", "ftp://example.com", "https://user:secret@example.com",
            "https://127.0.0.1/image", "https://[::1]/image", "https://[0:0:0:0:0:0:0:1]/image",
            "https://[::ffff:192.168.1.1]/image", "https://2130706433/image"
        ).forEach { url -> assertFalse(isAllowedPreviewUrl(Url(url)), url) }
    }
}
