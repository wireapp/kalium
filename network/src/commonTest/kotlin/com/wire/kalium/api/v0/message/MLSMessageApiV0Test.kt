/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.api.v0.message

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.v0.authenticated.MLSMessageApiV0
import com.wire.kalium.network.utils.isSuccessful
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

@ExperimentalCoroutinesApi
internal class MLSMessageApiV0Test : ApiTest() {

    @Test
    fun givenMessage_whenSendingMessage_theRequestShouldFail() =
        runTest {
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV0()
            val response = mlsMessageApi.sendMessage(MESSAGE)
            (response.isSuccessful())
        }

    @Test
    fun givenCommitBundle_whenSendingBundle_theRequestShouldFail() =
        runTest {
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV0()
            val response = mlsMessageApi.sendCommitBundle(COMMIT_BUNDLE)
            assertFalse(response.isSuccessful())
        }

    private companion object {
        val MESSAGE = "ApplicationMessage".encodeToByteArray()
        val WELCOME_MESSAGE = MLSMessageApi.WelcomeMessage("WelcomeMessage".encodeToByteArray())
        val COMMIT_BUNDLE = MLSMessageApi.CommitBundle("CommitBundle".encodeToByteArray())
    }

}
