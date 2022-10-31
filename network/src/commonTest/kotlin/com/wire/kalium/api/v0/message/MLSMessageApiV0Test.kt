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
class MLSMessageApiV0Test : ApiTest {

    @Test
    fun givenMessage_whenSendingMessage_theRequestShouldFail() =
        runTest {
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV0()
            val response = mlsMessageApi.sendMessage(MESSAGE)
            (response.isSuccessful())
        }

    @Test
    fun givenWelcomeMessage_whenSendingWelcomeMessage_theRequestShouldFail() =
        runTest {
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV0()
            val response = mlsMessageApi.sendWelcomeMessage(WELCOME_MESSAGE)
            assertFalse(response.isSuccessful())
        }

    @Test
    fun givenCommitBundle_whenSendingBundle_theRequestShouldFail() =
        runTest {
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV0()
            val response = mlsMessageApi.sendCommitBundle(COMMIT_BUNDLE)
            assertFalse(response.isSuccessful())
        }

    private companion object {
        val MESSAGE = MLSMessageApi.Message("ApplicationMessage".encodeToByteArray())
        val WELCOME_MESSAGE = MLSMessageApi.WelcomeMessage("WelcomeMessage".encodeToByteArray())
        val COMMIT_BUNDLE = MLSMessageApi.CommitBundle("CommitBundle".encodeToByteArray())
    }

}
