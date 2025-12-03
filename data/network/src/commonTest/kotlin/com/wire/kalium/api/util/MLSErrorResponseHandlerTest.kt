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
package com.wire.kalium.api.util

import com.wire.kalium.network.api.model.MLSErrorResponse
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.exceptions.MLSError
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.HttpResponseData
import com.wire.kalium.network.utils.MLSErrorResponseHandler
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MLSErrorResponseHandlerTest {

    private val subject = MLSErrorResponseHandler

    @Test
    fun givenNonMLSLabel_whenIntercepting_thenShouldReturnNull() = runTest {
        val result = subject.intercept(
            HttpResponseData(
                emptyMap(),
                HttpStatusCode.BadRequest,
                """{"label":"non-mls-label", "message":"hee hee"}""",
                KtxSerializer.json
            )
        )
        assertNull(result)
    }

    private suspend fun testLabelMatchesExpectedResult(label: String, message: String, expectedResult: MLSErrorResponse) {
        val result = subject.intercept(
            HttpResponseData(
                emptyMap(),
                HttpStatusCode.BadRequest,
                """{"label":"$label", "message":"$message"}""",
                KtxSerializer.json
            )
        )
        assertIs<NetworkResponse.Error>(result)
        assertIs<MLSError>(result.kException)
        assertEquals(expectedResult, result.kException.errorBody)
    }

    @Test
    fun givenKnownMLSErrorResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-stale-message", message, MLSErrorResponse.StaleMessage(message))
    }

    @Test
    fun givenSelfRemovalNotAllowedResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-self-removal-not-allowed", message, MLSErrorResponse.SelfRemovalNotAllowed(message))
    }

    @Test
    fun givenProtocolErrorResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-protocol-error", message, MLSErrorResponse.ProtocolError(message))
    }

    @Test
    fun givenNotEnabledResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-not-enabled", message, MLSErrorResponse.NotEnabled(message))
    }

    @Test
    fun givenInvalidLeafNodeIndexResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-invalid-leaf-node-index", message, MLSErrorResponse.InvalidLeafNodeIndex(message))
    }

    @Test
    fun givenInvalidLeafNodeSignatureResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-invalid-leaf-node-signature", message, MLSErrorResponse.InvalidLeafNodeSignature(message))
    }

    @Test
    fun givenGroupConversationMismatchResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-group-conversation-mismatch", message, MLSErrorResponse.GroupConversationMismatch(message))
    }

    @Test
    fun givenCommitMissingReferencesResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-commit-missing-references", message, MLSErrorResponse.CommitMissingReferences(message))
    }

    @Test
    fun givenClientSenderUserMismatchResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-client-sender-user-mismatch", message, MLSErrorResponse.ClientSenderUserMismatch(message))
    }

    @Test
    fun givenSubconversationJoinParentMissingResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult(
            "mls-subconv-join-parent-missing",
            message,
            MLSErrorResponse.SubconversationJoinParentMissing(message)
        )
    }

    @Test
    fun givenProposalNotFoundResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-proposal-not-found", message, MLSErrorResponse.ProposalNotFound(message))
    }

    @Test
    fun givenClientMismatchResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-client-mismatch", message, MLSErrorResponse.ClientMismatch(message))
    }

    @Test
    fun givenUnsupportedProposalResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-unsupported-proposal", message, MLSErrorResponse.UnsupportedProposal(message))
    }

    @Test
    fun givenUnsupportedMessageResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-unsupported-message", message, MLSErrorResponse.UnsupportedMessage(message))
    }

    @Test
    fun givenWelcomeMismatchResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-welcome-mismatch", message, MLSErrorResponse.WelcomeMismatch(message))
    }

    @Test
    fun givenMissingGroupInfoResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        testLabelMatchesExpectedResult("mls-missing-group-info", message, MLSErrorResponse.MissingGroupInfo(message))
    }

    @Test
    fun givenGroupOutOfSyncResponse_whenIntercepting_thenShouldReturnItProperly() = runTest {
        val message = "test message"
        val expectedUserIds = listOf(
            UserId("u1", "d1"),
            UserId("u2", "d2")
        )
        val expectedResult = MLSErrorResponse.GroupOutOfSync(expectedUserIds, message)
        val result = subject.intercept(
            HttpResponseData(
                emptyMap(),
                HttpStatusCode.BadRequest,
                """
                    |{
                    |  "label":"mls-group-out-of-sync",
                    |  "message":"$message",
                    |  "missing_users":[
                    |    {"domain": "d1", "id": "u1"},
                    |    {"domain": "d2", "id": "u2"}
                    |  ]
                    |}""".trimMargin(),
                KtxSerializer.json
            )
        )
        assertIs<NetworkResponse.Error>(result)
        assertIs<MLSError>(result.kException)
        assertEquals(expectedResult, result.kException.errorBody)
    }
}
