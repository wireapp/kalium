/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.api.v11

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.authenticated.conversation.ConversationHistorySettingsDTO
import com.wire.kalium.network.api.authenticated.conversation.HistoryClientId
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.v11.authenticated.ConversationHistoryApiV11
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

internal class ConversationHistoryApiV11Test : ApiTest() {

    @Test
    fun givenConversationId_whenUpdatingConversationHistorySettings_thenShouldPassPathParametersCorrectly() = runTest {
        val conversationId = ConversationId("conversation_id", "domain")
        val networkClient = mockAuthenticatedNetworkClient(
            statusCode = HttpStatusCode.Accepted,
            responseBody = "",
            assertion = {
                assertTrue("Url should end with conversation domain, id and history") {
                    url.fullPath.endsWith("${conversationId.domain}/${conversationId.value}/history")
                }
            }
        )
        val api = ConversationHistoryApiV11(networkClient)
        api.updateHistorySettingsForConversation(conversationId, ConversationHistorySettingsDTO.Private)
    }

    @Test
    fun givenPrivateParameters_whenUpdatingConversationHistorySettings_thenShouldSerializeBodyProperly() = runTest {
        val settings = ConversationHistorySettingsDTO.Private
        val expectedJson = """{"type":"private"}"""
        assertHistorySettingsJsonSerialization(settings, expectedJson)
    }

    @Test
    fun givenSharedParameters_whenUpdatingConversationHistorySettings_thenShouldSerializeBodyProperly() = runTest {
        val settings = ConversationHistorySettingsDTO.ShareWithNewMembers(42.days + 32.hours + 2.seconds)
        val expectedJson = """{"type":"shared","depth":${settings.depth.inWholeSeconds}}"""
        assertHistorySettingsJsonSerialization(settings, expectedJson)
    }

    private suspend fun assertHistorySettingsJsonSerialization(
        settings: ConversationHistorySettingsDTO,
        expectedJson: String
    ) {
        val conversationId = ConversationId("conversation_id", "domain")
        val networkClient = mockAuthenticatedNetworkClient(
            statusCode = HttpStatusCode.Accepted,
            responseBody = "",
            assertion = {
                assertJsonBodyContent(expectedJson)
            }
        )

        val api = ConversationHistoryApiV11(networkClient)
        api.updateHistorySettingsForConversation(conversationId, settings)
    }

    @Test
    fun givenConversationId_whenFetching_thenShouldPassPathParametersCorrectly() = runTest {
        val conversationId = ConversationId("conversation_id", "domain")
        val offset = 14UL
        val size = 42U
        val historyClientId = HistoryClientId("hClientId")
        val networkClient = mockAuthenticatedNetworkClient(
            statusCode = HttpStatusCode.OK,
            responseBody = """{"type":"private"}""",
            assertion = {
                val expectedPathSegments = listOf("history", conversationId.domain, conversationId.value, historyClientId.value)
                val actualPathSegments = url.pathSegments.filter { it.isNotBlank() && it != "v1" }
                assertContentEquals(expectedPathSegments, actualPathSegments)
                assertEquals(offset.toString(), url.parameters["offset"], "Offset was not set correctly.")
                assertEquals(size.toString(), url.parameters["size"], "Size was not set correctly.")
            }
        )

        val api = ConversationHistoryApiV11(networkClient)
        api.getPageOfMessagesForHistoryClient(conversationId, historyClientId, offset, size)
    }
}
