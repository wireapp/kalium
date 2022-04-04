package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationApiImp
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConversationApiTest: ApiTest {

    @Test
    fun given_whenCallingCreateNewConversation_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            CREATE_CONVERSATION_RESPONSE,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertJson()
                assertPost()
                assertPathEqual(PATH_CONVERSATIONS)
                assertBodyContent(CREATE_CONVERSATION_REQUEST.rawJson)
            }
        )
        val conversationApi: ConversationApi = ConversationApiImp(httpClient)
        val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

        assertTrue(result.isSuccessful())
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.validGroup.rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.valid
    }
}
