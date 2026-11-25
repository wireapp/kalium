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
    fun givenWireHostUrl_whenObfuscatingPath_thenHostStaysAndSchemePortUserInfoAndFragmentAreDropped() {
        val result = obfuscatePath(Url("https://alice:secret@nginz-https.anta.wire.link:8443/v5/self#section"))

        assertEquals("nginz-https.anta.wire.link/v5/self", result)
        assertFalse(result.contains("https:"))
        assertFalse(result.contains("8443"))
        assertFalse(result.contains("alice"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("section"))
    }

    @Test
    fun givenCustomBackendLookup_whenObfuscatingPath_thenCustomerDomainIsObfuscated() {
        val result = obfuscatePath(Url("https://prod-nginz-https.wire.com/v5/custom-backend/by-domain/acme-corp.example.com"))

        assertEquals("prod-nginz-https.wire.com/v5/custom-backend/by-domain/acm***", result)
        assertFalse(result.contains("acme-corp"))
    }

    @Test
    fun givenQualifiedIdInPath_whenObfuscatingPath_thenDomainAndIdAreObfuscated() {
        val domain = "anta.wire.link"
        val id = "9a1d6c0e-3b5f-4c5e-8f2a-1e0f7b6d4c3a"

        val result = obfuscatePath(Url("https://nginz-https.$domain/v9/conversations/$domain/$id/members/$domain/$id"))

        assertEquals("nginz-https.anta.wire.link/v9/conversations/ant***/9a1d6c0***/members/ant***/9a1d6c0***", result)
        assertFalse(result.substringAfter("/v9/").contains(domain))
        assertFalse(result.contains(id))
    }

    @Test
    fun givenLowercaseWordSegment_whenObfuscatingPath_thenItStaysReadable() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/some-new-endpoint/self"))

        assertEquals("nginz-https.anta.wire.link/v9/some-new-endpoint/self", result)
    }

    @Test
    fun givenSegmentsNoLongerThanTheirHint_whenObfuscatingPath_thenTheyAreReplaced() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/users/a.b/abc1234"))

        assertEquals("nginz-https.anta.wire.link/v9/users/{}/{}", result)
    }

    @Test
    fun givenSegmentWithReservedCharacters_whenObfuscatingPath_thenTheHintIsEncoded() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/clients/ab%2Fcdefgh"))

        assertEquals("nginz-https.anta.wire.link/v9/clients/ab%2Fcd***", result)
    }

    @Test
    fun givenCursorAndClientQueryValues_whenObfuscatingPath_thenOnlyAThreeCharacterHintIsKept() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?since=abc123&client=5f2b9e7c4d1a&cursor=eyJwYWdl"))

        assertEquals("nginz-https.anta.wire.link/v9/notifications?since=abc***&client=5f2***&cursor=eyJ***", result)
    }

    @Test
    fun givenCredentialQueryValues_whenObfuscatingPath_thenTheyAreFullyMasked() {
        val result = obfuscatePath(
            Url("https://nginz-https.anta.wire.link/v9/conversations/join?code=9275abcd&key=abcdef12&sig=abcdef123&access_token=eyJhbGci")
        )

        assertEquals("nginz-https.anta.wire.link/v9/conversations/join?code=***&key=***&sig=***&access_token=***", result)
    }

    @Test
    fun givenPushTokenInPath_whenObfuscatingPath_thenItIsFullyMasked() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/push/tokens/dGVzdC1wdXNoLXRva2Vu:APA91bHx"))

        assertEquals("nginz-https.anta.wire.link/v9/push/tokens/{}", result)
    }

    @Test
    fun givenPagingQueryValues_whenObfuscatingPath_thenAsciiDigitsStayInClearAndOtherValuesAreMasked() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?size=500&limit=abc&offset=20&page=0"))

        assertEquals("nginz-https.anta.wire.link/v9/notifications?size=500&limit=***&offset=20&page=0", result)
    }

    @Test
    fun givenPagingQueryValueWithNonAsciiDigits_whenObfuscatingPath_thenItIsMasked() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?size=%D9%A5%D9%A0&limit=&page=1.5"))

        assertEquals("nginz-https.anta.wire.link/v9/notifications?size=***&limit=***&page=***", result)
    }

    @Test
    fun givenCredentialKeysWithDigits_whenObfuscatingPath_thenTheyAreStillMasked() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/conversations/join?code=123456&key=7890&token=42"))

        assertEquals("nginz-https.anta.wire.link/v9/conversations/join?code=***&key=***&token=***", result)
    }

    @Test
    fun givenUppercaseWireHost_whenObfuscatingPath_thenHostStaysInClear() {
        assertEquals("NGINZ-HTTPS.Anta.WIRE.Link/v9/self", obfuscatePath(Url("https://NGINZ-HTTPS.Anta.WIRE.Link/v9/self")))
    }

    @Test
    fun givenWireOwnedHosts_whenObfuscatingPath_thenHostStaysInClear() {
        listOf("wire.com", "prod-nginz-https.wire.com", "a.wire.link", "x.zinfra.io").forEach { host ->
            assertEquals("$host/v9/self", obfuscatePath(Url("https://$host/v9/self")), host)
        }
    }

    @Test
    fun givenCustomerHost_whenObfuscatingPath_thenHostKeepsOnlyAThreeCharacterHint() {
        val result = obfuscatePath(Url("https://nginz-https.acme-corp.com/v9/self"))

        assertEquals("ngi***/v9/self", result)
        assertFalse(result.contains("acme"))
    }

    @Test
    fun givenLookAlikeHosts_whenObfuscatingPath_thenHostIsNotTreatedAsWireOwned() {
        assertEquals("evi***/v9/self", obfuscatePath(Url("https://evilwire.com/v9/self")))
        assertEquals("wir***/v9/self", obfuscatePath(Url("https://wire.com.attacker.net/v9/self")))
    }

    @Test
    fun givenHostNoLongerThanItsHint_whenObfuscatingPath_thenHostIsReplaced() {
        assertEquals("{}/v9/self", obfuscatePath(Url("https://a.b/v9/self")))
    }

    @Test
    fun givenHostLessUrl_whenObfuscatingPath_thenNoHostIsInvented() {
        assertEquals("/v9/users/{}?size=5", obfuscatePath(Url("/v9/users/abc1234?size=5")))
    }

    @Test
    fun givenQueryValueWithReservedCharactersInItsHint_whenObfuscatingPath_thenTheHintIsEncoded() {
        val result = obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/notifications?since=a%26b%3Dcdef"))

        assertEquals("nginz-https.anta.wire.link/v9/notifications?since=a%26b***", result)
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
            assertEquals("nginz-https.anta.wire.link/v9/$path", obfuscatePath(Url("https://nginz-https.anta.wire.link/v9/$path")), path)
        }
    }
}
