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

import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun givenFederatedBackendUrl_whenObfuscatingPath_thenSchemeHostPortUserInfoAndFragmentAreDropped() {
        val result = obfuscatePath(Url("https://alice:secret@nginz-https.anta.wire.link:8443/v5/self#section"))

        assertEquals("/v5/self", result)
        assertFalse(result.contains("anta.wire.link"))
        assertFalse(result.contains("https"))
        assertFalse(result.contains("alice"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("section"))
    }

    @Test
    fun givenCustomBackendLookup_whenObfuscatingPath_thenCustomerDomainIsReplaced() {
        val result = obfuscatePath(Url("https://prod-nginz-https.wire.com/v5/custom-backend/by-domain/acme-corp.example.com"))

        assertEquals("/v5/custom-backend/by-domain/{}", result)
        assertFalse(result.contains("acme-corp"))
    }

    @Test
    fun givenQualifiedIdInPath_whenObfuscatingPath_thenDomainAndIdAreReplaced() {
        val domain = "anta.wire.link"
        val id = "9a1d6c0e-3b5f-4c5e-8f2a-1e0f7b6d4c3a"

        val result = obfuscatePath(Url("https://nginz-https.$domain/v9/conversations/$domain/$id/members/$domain/$id"))

        assertEquals("/v9/conversations/{}/{}/members/{}/{}", result)
        assertFalse(result.contains(domain))
        assertFalse(result.contains(id))
    }

    @Test
    fun givenCursorAndClientQueryValues_whenObfuscatingPath_thenValuesAreFullyMasked() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?since=abc123&client=5f2b9e7c4d1a&cursor=eyJwYWdl"))

        assertEquals("/v9/notifications?since=***&client=***&cursor=***", result)
        assertFalse(result.contains("5f2"))
        assertFalse(result.contains("eyJ"))
    }

    @Test
    fun givenSignatureLikeQueryValue_whenObfuscatingPath_thenValueIsMaskedWithoutPrefix() {
        val result = obfuscatePath(Url("https://assets.example.test/v9/assets/v4/key?sig=abcdef123&token=xyz789"))

        assertEquals("/v9/assets/v4/{}?sig=***&token=***", result)
        assertFalse(result.contains("abc"))
        assertFalse(result.contains("xyz"))
    }

    @Test
    fun givenNumericPagingQueryValues_whenObfuscatingPath_thenOnlyNumericPagingValuesStayReadable() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?size=500&limit=abc&offset=20"))

        assertEquals("/v9/notifications?size=500&limit=***&offset=20", result)
    }

    @Test
    fun givenRealEndpointPaths_whenObfuscatingPath_thenStaticSegmentsStayReadable() {
        val endpoints = listOf(
            "api-version",
            "access",
            "access/logout",
            "login",
            "register",
            "activate/send",
            "verification-code/send",
            "self",
            "self/handle",
            "self/email",
            "self/supported-protocols",
            "users/{}/{}",
            "users/{}/{}/prekeys/{}",
            "users/list-clients/v2",
            "users/list-prekeys",
            "list-users",
            "clients",
            "clients/{}",
            "clients/{}/prekeys",
            "clients/{}/nonce",
            "clients/{}/access-token",
            "push/tokens",
            "push/tokens/{}",
            "conversations/list",
            "conversations/list-ids",
            "conversations/self",
            "conversations/join",
            "conversations/one2one/{}/{}",
            "one2one-conversations/{}/{}",
            "conversations/{}/{}/members/{}/{}",
            "conversations/{}/{}/name",
            "conversations/{}/{}/access",
            "conversations/{}/{}/receipt-mode",
            "conversations/{}/{}/message-timer",
            "conversations/{}/{}/protocol",
            "conversations/{}/{}/groupinfo",
            "conversations/{}/{}/subconversations/{}/self",
            "conversations/{}/{}/proteus/messages",
            "conversations/{}/typing",
            "conversations/{}/bots",
            "conversations/{}/code",
            "conversations/{}/{}/add-permission",
            "conversations/{}/{}/history",
            "history/{}/{}/{}",
            "mls/messages",
            "mls/messages/targeted",
            "mls/messages/transient",
            "mls/messages/transient-targeted",
            "mls/commit-bundles",
            "mls/key-packages/self/{}/count",
            "mls/key-packages/claim/{}/{}",
            "mls/public-keys",
            "mls/reset-conversation",
            "connections/{}/{}",
            "list-connections",
            "notifications",
            "notifications/last",
            "events",
            "await",
            "properties",
            "properties/labels",
            "properties-values",
            "feature-configs",
            "calls/config/v2",
            "assets",
            "assets/{}/{}",
            "assets/v3",
            "assets/v4/{}/{}",
            "teams/{}/members",
            "teams/{}/get-members-by-ids-using-post",
            "teams/{}/legalhold/{}/approve",
            "teams/{}/services/whitelisted",
            "teams/{}/collaborators",
            "teams/{}/apps",
            "teams/{}/conversations/{}",
            "upgrade-personal-to-team",
            "meetings/list",
            "search/contacts",
            "proxy/giphy/v1/gifs/search",
            "proxy/giphy/v1/gifs/trending",
            "proxy/giphy/v1/gifs/tags",
            "sso/initiate-login/{}",
            "sso/finalize-login",
            "sso/metadata",
            "sso/settings",
            "sso/get-by-email",
            "get-domain-registration",
            "custom-backend/by-domain/{}",
            "cookies",
            "cookies/remove",
            "system/settings",
            "time",
            "federation",
            "roots.pem",
        )

        endpoints.forEach { path ->
            assertEquals("/v9/$path", obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/$path")), path)
        }
    }
}
