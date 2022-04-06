package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationApiImpl
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConversationApiTest : ApiTest {

    @Test
    fun givenACreateNewConversationRequest_whenCallingCreateNewConversation_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
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
        val conversationApi: ConversationApi = ConversationApiImpl(httpClient)
        val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

        assertTrue(result.isSuccessful())
    }

    @Test
    fun givenARequestToUpdateMuteStatus_whenCallingUpdateConversationState_thenTheRequestShouldBeOK() = runTest {
        val conversationId = "conv-id"
        val domain = "domain"
        val httpClient = mockAuthenticatedHttpClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPut()
                assertPathEqual("$PATH_CONVERSATIONS/$domain/$conversationId$PATH_SELF")
                assertBodyContent(MEMBER_UPDATE_REQUEST.rawJson)
            }
        )

        val conversationApi = ConversationApiImpl(httpClient)
        val result = conversationApi.updateConversationMemberState(
            MEMBER_UPDATE_REQUEST.serializableData, ConversationId(conversationId, domain)
        )

        assertTrue(result.isSuccessful())
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_SELF = "/self"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.validGroup.rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.valid
        val MEMBER_UPDATE_REQUEST = MemberUpdateRequestJson.valid
    }
}
