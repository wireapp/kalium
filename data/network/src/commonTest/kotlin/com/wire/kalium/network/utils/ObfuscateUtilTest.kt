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

package com.wire.kalium.network.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ObfuscateUtilTest {
    @Test
    fun jsonLogRedactsAuthenticationInfoLikeHttpHeaders() {
        val result = obfuscatedJsonMessage(
            """{"Authentication-Info":"nextnonce=secret","Location":"signed-url","message":"safe"}"""
        )
        val fields = Json.parseToJsonElement(result).jsonObject

        assertEquals("***", fields["Authentication-Info"]?.jsonPrimitive?.content)
        assertEquals("***", fields["Location"]?.jsonPrimitive?.content)
        assertEquals("safe", fields["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun obfuscatePathDropsFederatedHostUserInfoAndFragment() {
        val result = obfuscatePath(Url("https://user:pw@staging.customer-example.com/conversations?x=1#frag"))

        assertEquals("/conversations?x=***", result)
    }

    @Test
    fun obfuscatePathReplacesQualifiedIdWithPlaceholders() {
        val result = obfuscatePath(
            Url("https://staging.customer-example.com/v7/conversations/customer-example.com/3f2a6b2e-1c4d-4e5f-8a9b-0c1d2e3f4a5b/members")
        )

        assertEquals("/v7/conversations/{}/{}/members", result)
        assertFalse(result.contains("customer-example"))
        assertFalse(result.contains("3f2a6b2e"))
    }

    @Test
    fun obfuscatePathReplacesCursorAndClientQueryValues() {
        val result = obfuscatePath(Url("https://example.test/notifications?cursor=secretcursor&client=abcd1234ef&size=100&q=alice"))

        assertEquals("/notifications?cursor=***&client=***&size=100&q=***", result)
    }

    @Test
    fun obfuscatePathMasksNonNumericPagingValues() {
        val result = obfuscatePath(Url("https://example.test/users?size=abc&page=2"))

        assertEquals("/users?size=***&page=2", result)
    }
}
