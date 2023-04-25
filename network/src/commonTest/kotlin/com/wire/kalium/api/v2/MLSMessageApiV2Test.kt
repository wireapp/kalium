/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.api.v2

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.SendMLSMessageResponseJson
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.v0.authenticated.MLSMessageApiV0
import com.wire.kalium.network.api.v2.authenticated.MLSMessageApiV2
import com.wire.kalium.network.serialization.Mls
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class MLSMessageApiV2Test : ApiTest() {

    @Test
    fun givenMessage_whenSendingMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SendMLSMessageResponseJson.validMessageSentJson.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertContentType(ContentType.Message.Mls)
                    assertPathEqual(PATH_MESSAGE)
                }
            )
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV2(networkClient)
            val response = mlsMessageApi.sendMessage(MESSAGE)
            assertTrue(response.isSuccessful())
        }

    @Test
    fun givenWelcomeMessage_whenSendingWelcomeMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SendMLSMessageResponseJson.validMessageSentJson.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertContentType(ContentType.Message.Mls)
                    assertPathEqual(PATH_WELCOME_MESSAGE)
                }
            )
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV2(networkClient)
            val response = mlsMessageApi.sendWelcomeMessage(WELCOME_MESSAGE)
            assertTrue(response.isSuccessful())
        }

    @Test
    fun givenCommitBundle_whenSendingBundle_theRequestShouldFail() =
        runTest {
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV0()
            val response = mlsMessageApi.sendCommitBundle(COMMIT_BUNDLE)
            assertFalse(response.isSuccessful())
        }

    private companion object {
        const val PATH_MESSAGE = "/mls/messages"
        const val PATH_WELCOME_MESSAGE = "/mls/welcome"
        val MESSAGE = MLSMessageApi.Message("ApplicationMessage".encodeToByteArray())
        val WELCOME_MESSAGE = MLSMessageApi.WelcomeMessage("WelcomeMessage".encodeToByteArray())
        val COMMIT_BUNDLE = MLSMessageApi.CommitBundle("CommitBundle".encodeToByteArray())
    }

}
