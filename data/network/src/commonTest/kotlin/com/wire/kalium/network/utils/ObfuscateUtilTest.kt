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
    fun givenCustomBackendLookup_whenObfuscatingPath_thenCustomerDomainIsObfuscated() {
        val result = obfuscatePath(Url("https://prod-nginz-https.wire.com/v5/custom-backend/by-domain/acme-corp.example.com"))

        assertEquals("/v5/custom-backend/by-domain/acm***", result)
        assertFalse(result.contains("acme-corp"))
    }

    @Test
    fun givenQualifiedIdInPath_whenObfuscatingPath_thenDomainAndIdAreObfuscated() {
        val domain = "anta.wire.link"
        val id = "9a1d6c0e-3b5f-4c5e-8f2a-1e0f7b6d4c3a"

        val result = obfuscatePath(Url("https://nginz-https.$domain/v9/conversations/$domain/$id/members/$domain/$id"))

        assertEquals("/v9/conversations/ant***/9a1d6c0***/members/ant***/9a1d6c0***", result)
        assertFalse(result.contains(domain))
        assertFalse(result.contains(id))
    }

    @Test
    fun givenLowercaseWordSegment_whenObfuscatingPath_thenItStaysReadable() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/some-new-endpoint/self"))

        assertEquals("/v9/some-new-endpoint/self", result)
    }

    @Test
    fun givenSegmentsNoLongerThanTheirHint_whenObfuscatingPath_thenTheyAreReplaced() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/users/a.b/abc1234"))

        assertEquals("/v9/users/{}/{}", result)
    }

    @Test
    fun givenSegmentWithReservedCharacters_whenObfuscatingPath_thenTheHintIsEncoded() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/clients/ab%2Fcdefgh"))

        assertEquals("/v9/clients/ab%2Fcd***", result)
    }

    @Test
    fun givenCursorAndClientQueryValues_whenObfuscatingPath_thenOnlyAThreeCharacterHintIsKept() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?since=abc123&client=5f2b9e7c4d1a&cursor=eyJwYWdl"))

        assertEquals("/v9/notifications?since=abc***&client=5f2***&cursor=eyJ***", result)
    }

    @Test
    fun givenSignatureLikeQueryValue_whenObfuscatingPath_thenOnlyAThreeCharacterHintIsKept() {
        val result = obfuscatePath(Url("https://assets.example.test/v9/assets/v4/3-1-9a1d6c0e?sig=abcdef123&token=xyz789"))

        assertEquals("/v9/assets/v4/3-1-9a1***?sig=abc***&token=xyz***", result)
    }

    @Test
    fun givenQueryValuesOfThreeCharactersOrLess_whenObfuscatingPath_thenTheyAreFullyMasked() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?size=500&limit=abc&offset=20"))

        assertEquals("/v9/notifications?size=***&limit=***&offset=***", result)
    }

    @Test
    fun givenQueryValueWithReservedCharactersInItsHint_whenObfuscatingPath_thenTheHintIsEncoded() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?since=a%26b%3Dcdef"))

        assertEquals("/v9/notifications?since=a%26b***", result)
    }

    @Test
    fun givenRealEndpointPaths_whenObfuscatingPath_thenApiWordsStayReadable() {
        val endpoints = listOf(
            "self/handle",
            "conversations/list-ids",
            "mls/key-packages/self/{}/count",
            "assets/v4/{}/{}",
            "custom-backend/by-domain/{}",
            "feature-configs",
        )

        endpoints.forEach { path ->
            assertEquals("/v9/$path", obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/$path")), path)
        }
    }
}
