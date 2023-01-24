package com.wire.kalium.api.v3

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.EventContentDTOJson
import com.wire.kalium.model.conversation.UpdateConversationAccessRequestJson
import com.wire.kalium.model.conversation.ConversationResponseJson
import com.wire.kalium.model.conversation.CreateConversationRequestJson
import com.wire.kalium.model.conversation.SubconversationDeleteRequestJson
import com.wire.kalium.model.conversation.SubconversationDetailsResponseJson
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v3.authenticated.ConversationApiV3
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationApiV3Test : ApiTest {

    @Test
    fun givenACreateNewConversationRequest_whenCallingCreateNewConversaton_thenTheRequestShouldBeConfiguredOK() = runTest {
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
        val conversationApi: ConversationApi = ConversationApiV3(networkClient)
        val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

        assertTrue(result.isSuccessful())
    }

    @Test
    fun whenUpdatingAccessRole_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE), setOf(ConversationAccessRoleDTO.TEAM_MEMBER)
        )
        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPut()
                assertPathEqual("/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/access")
                assertJsonBodyContent(ACCESS_ROLE_UPDATE_REQUEST.rawJson)
            }
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.updateAccess(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles)
    }

    @Test
    fun givenAccessUnchangedResponse_whenUpdatingAccessRole_thenAccessUnchangedIsPropagated() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE, ConversationAccessDTO.INVITE), setOf()
        )
        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.NoContent
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.updateAccess(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUnchanged>>(it)
        }
    }

    @Test
    fun givenSuccessAccessUpdateResponse_whenUpdatingAccessRole_thenAccessUpdateEventIsPropagated() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE, ConversationAccessDTO.INVITE), setOf()
        )
        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validAccessUpdate.rawJson, statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.updateAccess(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUpdated>>(it)
        }
    }

    @Test
    fun givenRequest_whenFetchingSubconversationDetails_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SubconversationDetailsResponseJson.v3.rawJson, statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub")
            }
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.fetchSubconversationDetails(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), "sub")
    }

    @Test
    fun givenSuccessSubconversationDetails_whenFetchingSubconversationDetails_thenResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SubconversationDetailsResponseJson.v3.rawJson, statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.fetchSubconversationDetails(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), "sub").also {
            assertIs<NetworkResponse.Success<SubconversationResponse>>(it)
        }
    }

    @Test
    fun givenRequest_whenFetchingSubconversationGroupInfo_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "groupinfo".encodeToByteArray(), statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub/groupinfo")
            }
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.fetchSubconversationGroupInfo(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), "sub").also {
            assertIs<NetworkResponse.Success<ByteArray>>(it)
        }
    }

    @Test
    fun givenRequest_whenDeletingSubconversation_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "groupinfo".encodeToByteArray(), statusCode = HttpStatusCode.OK,
            assertion = {
                assertDelete()
                assertPathEqual("/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub")
                assertJsonBodyContent(SubconversationDeleteRequestJson.v3.rawJson)
            }
        )

        val deleteRequest = SubconversationDeleteRequest(43UL, "groupid")
        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.deleteSubconversation(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), "sub", deleteRequest)
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.v3.rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.v3
        val ACCESS_ROLE_UPDATE_REQUEST = UpdateConversationAccessRequestJson.v3
    }
}
