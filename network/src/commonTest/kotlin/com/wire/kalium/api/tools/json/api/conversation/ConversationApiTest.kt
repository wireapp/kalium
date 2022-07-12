package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.api.notification.EventContentDTOJson
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationApiImpl
import com.wire.kalium.network.api.conversation.model.ConversationAccessData
import com.wire.kalium.network.api.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.network.api.model.ConversationAccess
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationApiTest : ApiTest {

    @Test
    fun givenACreateNewConversationRequest_whenCallingCreateNewConversation_thenTheRequestShouldBeConfiguredOK() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            CREATE_CONVERSATION_RESPONSE,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertJson()
                assertPost()
                assertPathEqual(PATH_CONVERSATIONS)
                assertJsonBodyContent(CREATE_CONVERSATION_REQUEST.rawJson)
            }
        )
        val conversationApi: ConversationApi = ConversationApiImpl(networkClient)
        val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

        assertTrue(result.isSuccessful())
    }

    @Test
    fun givenARequestToUpdateMuteStatus_whenCallingUpdateConversationState_thenTheRequestShouldBeConfiguredOK() = runTest {
        val conversationId = "conv-id"
        val domain = "domain"
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPut()
                assertPathEqual("$PATH_CONVERSATIONS/$domain/$conversationId$PATH_SELF")
                assertJsonBodyContent(MEMBER_UPDATE_REQUEST.rawJson)
            }
        )

        val conversationApi = ConversationApiImpl(networkClient)
        val result = conversationApi.updateConversationMemberState(
            MEMBER_UPDATE_REQUEST.serializableData, ConversationId(conversationId, domain)
        )

        assertTrue(result.isSuccessful())
    }

    @Test
    fun givenFetchConversationsIds_whenCallingFetchConversations_thenTheRequestShouldBeConfiguredOK() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = CONVERSATION_IDS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertPathEqual(PATH_CONVERSATIONS_IDS)
            }
        )

        val conversationApi = ConversationApiImpl(networkClient)
        conversationApi.fetchConversationsIds(pagingState = null)
    }

    @Test
    fun givenFetchConversationsDetails_whenCallingFetchWithIdList_thenTheRequestShouldBeConfiguredOK() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            CONVERSATION_DETAILS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertJsonBodyContent(CREATE_CONVERSATION_IDS_REQUEST.rawJson)
                assertPathEqual(PATH_CONVERSATIONS_LIST_V2)
            }
        )

        val conversationApi = ConversationApiImpl(networkClient)
        conversationApi.fetchConversationsListDetails(
            listOf(
                ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
                ConversationId("f4680835-2cfe-4d4d-8491-cbb201bd5c2b", "anta.wire.link")
            )
        )
    }

    @Test
    fun whenUpdatingAccessRole_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val accessRoles = ConversationAccessData(
            setOf(ConversationAccess.PRIVATE, ConversationAccess.INVITE), null
        )
        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPut()
                assertPathEqual("/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/access")
            }
        )

        val conversationApi = ConversationApiImpl(networkClient)
        conversationApi.updateAccessRole(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles)
    }

    @Test
    fun givenAccessUnchangedResponse_whenUpdatingAccessRole_thenAccessUnchangedIsPropagated() = runTest {
        val accessRoles = ConversationAccessData(
            setOf(ConversationAccess.PRIVATE, ConversationAccess.INVITE), null
        )
        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.NoContent
        )

        val conversationApi = ConversationApiImpl(networkClient)
        conversationApi.updateAccessRole(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUnchanged>>(it)
        }
    }

    @Test
    fun givenSuccessAccessUpdateResponse_whenUpdatingAccessRole_thenAccessUpdateEventIsPropagated() = runTest {
        val accessRoles = ConversationAccessData(
            setOf(ConversationAccess.PRIVATE, ConversationAccess.INVITE), null
        )
        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.valid.rawJson, statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiImpl(networkClient)
        conversationApi.updateAccessRole(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUpdated>>(it)
        }
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_CONVERSATIONS_LIST_V2 = "/conversations/list/v2"
        const val PATH_CONVERSATIONS_IDS = "/conversations/list-ids"
        const val PATH_SELF = "/self"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.validGroup.rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.valid
        val CREATE_CONVERSATION_IDS_REQUEST = ConversationListIdsResponseJson.validRequestIds
        val CONVERSATION_IDS_RESPONSE = ConversationListIdsResponseJson.validGetIds
        val CONVERSATION_DETAILS_RESPONSE = ConversationDetailsResponse.validGetDetailsForIds
        val MEMBER_UPDATE_REQUEST = MemberUpdateRequestJson.valid
    }
}
