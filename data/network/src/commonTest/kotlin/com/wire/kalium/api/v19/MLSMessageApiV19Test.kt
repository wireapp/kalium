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

package com.wire.kalium.api.v19

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.mocks.responses.SendMLSMessageResponseJson
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.api.model.MLSErrorResponse
import com.wire.kalium.network.api.v18.authenticated.MLSMessageApiV18
import com.wire.kalium.network.api.v19.authenticated.MLSMessageApiV19
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.MLSError
import com.wire.kalium.network.serialization.Mls
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class MLSMessageApiV19Test : ApiTest() {

    @Test
    fun givenTargetedMessageBatch_whenSending_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SendMLSMessageResponseJson.validMessageSentJson.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_TARGETED_MESSAGE)
            },
        )

        val response = MLSMessageApiV19(networkClient).sendTargetedMessage(TARGETED_MESSAGE_BATCH)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenTargetedMessageBatchFailsWithStaleMessage_whenSending_thenReturnsMlsError() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid(
                GenericAPIErrorResponse(
                    code = HttpStatusCode.Conflict.value,
                    message = "The conversation epoch in a message is too old",
                    label = "mls-stale-message"
                )
            ).rawJson,
            statusCode = HttpStatusCode.Conflict,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_TARGETED_MESSAGE)
            },
        )

        val response = MLSMessageApiV19(networkClient).sendTargetedMessage(TARGETED_MESSAGE_BATCH)

        assertIs<NetworkResponse.Error>(response)
        assertIs<MLSError>(response.kException)
        assertIs<MLSErrorResponse.StaleMessage>(response.kException.errorBody)
    }

    @Test
    fun givenApiV18_whenSendingTargetedMessageBatch_thenReturnsApiNotSupported() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SendMLSMessageResponseJson.validMessageSentJson.rawJson,
            statusCode = HttpStatusCode.Created,
        )

        val response = MLSMessageApiV18(networkClient).sendTargetedMessage(TARGETED_MESSAGE_BATCH)

        assertIs<NetworkResponse.Error>(response)
        assertIs<APINotSupported>(response.kException)
    }

    @Test
    fun givenTransientMessage_whenSending_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SendMLSMessageResponseJson.validMessageSentJson.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_TRANSIENT_MESSAGE)
            },
        )

        val response = MLSMessageApiV19(networkClient).sendTransientMessage(TRANSIENT_MESSAGE)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenTransientMessageFailsWithStaleMessage_whenSending_thenReturnsMlsError() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid(
                GenericAPIErrorResponse(
                    code = HttpStatusCode.Conflict.value,
                    message = "The conversation epoch in a message is too old",
                    label = "mls-stale-message"
                )
            ).rawJson,
            statusCode = HttpStatusCode.Conflict,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_TRANSIENT_MESSAGE)
            },
        )

        val response = MLSMessageApiV19(networkClient).sendTransientMessage(TRANSIENT_MESSAGE)

        assertIs<NetworkResponse.Error>(response)
        assertIs<MLSError>(response.kException)
        assertIs<MLSErrorResponse.StaleMessage>(response.kException.errorBody)
    }

    @Test
    fun givenApiV18_whenSendingTransientMessage_thenReturnsApiNotSupported() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SendMLSMessageResponseJson.validMessageSentJson.rawJson,
            statusCode = HttpStatusCode.Created,
        )

        val response = MLSMessageApiV18(networkClient).sendTransientMessage(TRANSIENT_MESSAGE)

        assertIs<NetworkResponse.Error>(response)
        assertIs<APINotSupported>(response.kException)
    }

    @Test
    fun givenTransientTargetedMessageBatch_whenSending_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SendMLSMessageResponseJson.validMessageSentJson.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_TRANSIENT_TARGETED_MESSAGE)
            },
        )

        val response = MLSMessageApiV19(networkClient).sendTransientTargetedMessage(TRANSIENT_TARGETED_MESSAGE_BATCH)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenTransientTargetedMessageBatchFailsWithStaleMessage_whenSending_thenReturnsMlsError() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid(
                GenericAPIErrorResponse(
                    code = HttpStatusCode.Conflict.value,
                    message = "The conversation epoch in a message is too old",
                    label = "mls-stale-message"
                )
            ).rawJson,
            statusCode = HttpStatusCode.Conflict,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_TRANSIENT_TARGETED_MESSAGE)
            },
        )

        val response = MLSMessageApiV19(networkClient).sendTransientTargetedMessage(TRANSIENT_TARGETED_MESSAGE_BATCH)

        assertIs<NetworkResponse.Error>(response)
        assertIs<MLSError>(response.kException)
        assertIs<MLSErrorResponse.StaleMessage>(response.kException.errorBody)
    }

    @Test
    fun givenApiV18_whenSendingTransientTargetedMessageBatch_thenReturnsApiNotSupported() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SendMLSMessageResponseJson.validMessageSentJson.rawJson,
            statusCode = HttpStatusCode.Created,
        )

        val response = MLSMessageApiV18(networkClient).sendTransientTargetedMessage(TRANSIENT_TARGETED_MESSAGE_BATCH)

        assertIs<NetworkResponse.Error>(response)
        assertIs<APINotSupported>(response.kException)
    }

    private companion object {
        const val PATH_TARGETED_MESSAGE = "mls/messages/targeted"
        const val PATH_TRANSIENT_MESSAGE = "mls/messages/transient"
        const val PATH_TRANSIENT_TARGETED_MESSAGE = "mls/messages/transient-targeted"
        val TARGETED_MESSAGE_BATCH = MLSMessageApi.TargetedMessageBatch("TargetedMessageBatch".encodeToByteArray())
        val TRANSIENT_MESSAGE = MLSMessageApi.TransientMessage("TransientMessage".encodeToByteArray())
        val TRANSIENT_TARGETED_MESSAGE_BATCH =
            MLSMessageApi.TargetedMessageBatch("TransientTargetedMessageBatch".encodeToByteArray())
    }
}
