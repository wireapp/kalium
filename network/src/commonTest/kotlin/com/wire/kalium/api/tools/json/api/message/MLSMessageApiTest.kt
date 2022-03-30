package com.wire.kalium.api.tools.json.api.message

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.IgnoreIOS
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.api.message.MLSMessageApiImpl
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessageApiImp
import com.wire.kalium.network.api.message.provideEnvelopeProtoMapper
import com.wire.kalium.network.serialization.Mls
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class MLSMessageApiTest: ApiTest {

    @Test
    fun givenMessage_whenSendingMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
                "",
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertContentType(ContentType.Message.Mls)
                    assertPathEqual(PATH_MESSAGE)
                }
            )
            val mlsMessageApi: MLSMessageApi = MLSMessageApiImpl(httpClient)
            val response = mlsMessageApi.sendMessage(MESSAGE)
            assertTrue(response.isSuccessful())
        }

    @Test
    fun givenWelcomeMessage_whenSendingWelcomeMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
                "",
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertContentType(ContentType.Message.Mls)
                    assertPathEqual(PATH_WELCOME_MESSAGE)
                }
            )
            val mlsMessageApi: MLSMessageApi = MLSMessageApiImpl(httpClient)
            val response = mlsMessageApi.sendWelcomeMessage(WELCOME_MESSAGE)
            assertTrue(response.isSuccessful())
        }

    private companion object {
        const val PATH_MESSAGE = "/mls/messages"
        const val PATH_WELCOME_MESSAGE = "/mls/welcome"
        val MESSAGE = MLSMessageApi.Message("ApplicationMessage".encodeToByteArray())
        val WELCOME_MESSAGE = MLSMessageApi.WelcomeMessage("WelcomeMessage".encodeToByteArray())
    }

}
