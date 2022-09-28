package com.wire.kalium.api.v2

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.conversation.ConversationDetailsResponse
import com.wire.kalium.model.conversation.ConversationListIdsResponseJson
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v2.authenticated.ConversationApiV2
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ConversationApiV2Test : ApiTest {
    @Test
    fun givenFetchConversationsDetails_whenCallingFetchWithIdList_thenTheRequestShouldBeConfiguredOK() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            CONVERSATION_DETAILS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertJsonBodyContent(CREATE_CONVERSATION_IDS_REQUEST.rawJson)
                assertPathEqual(PATH_CONVERSATIONS_LIST)
            }
        )

        val conversationApi = ConversationApiV2(networkClient)
        conversationApi.fetchConversationsListDetails(
            listOf(
                ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
                ConversationId("f4680835-2cfe-4d4d-8491-cbb201bd5c2b", "anta.wire.link")
            )
        )
    }

    private companion object {
        const val PATH_CONVERSATIONS_LIST = "/conversations/list"
        val CREATE_CONVERSATION_IDS_REQUEST = ConversationListIdsResponseJson.validRequestIds
        val CONVERSATION_DETAILS_RESPONSE = ConversationDetailsResponse.validGetDetailsForIds
    }
}
