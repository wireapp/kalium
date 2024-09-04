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

package com.wire.kalium.api.v0.conversation

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.AddServiceResponseJson
import com.wire.kalium.mocks.responses.EventContentDTOJson
import com.wire.kalium.mocks.responses.EventContentDTOJson.validGenerateGuestRoomLink
import com.wire.kalium.mocks.responses.conversation.ConversationDetailsResponse
import com.wire.kalium.mocks.responses.conversation.ConversationListIdsResponseJson
import com.wire.kalium.mocks.responses.conversation.ConversationResponseJson
import com.wire.kalium.mocks.responses.conversation.CreateConversationRequestJson
import com.wire.kalium.mocks.responses.conversation.MemberUpdateRequestJson
import com.wire.kalium.mocks.responses.conversation.SendTypingStatusNotificationRequestJson
import com.wire.kalium.mocks.responses.conversation.UpdateConversationAccessRequestJson
import com.wire.kalium.network.api.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.conversation.TypingIndicatorStatus
import com.wire.kalium.network.api.authenticated.conversation.TypingIndicatorStatusDTO
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.JoinConversationRequestV0
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v0.authenticated.ConversationApiV0
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class ConversationApiV0Test : ApiTest() {

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
        val conversationApi: ConversationApi = ConversationApiV0(networkClient)
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

        val conversationApi = ConversationApiV0(networkClient)
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

        val conversationApi = ConversationApiV0(networkClient)
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

        val conversationApi = ConversationApiV0(networkClient)
        conversationApi.fetchConversationsListDetails(
            listOf(
                ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
                ConversationId("f4680835-2cfe-4d4d-8491-cbb201bd5c2b", "anta.wire.link")
            )
        )
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
                assertJsonBodyContent(UPDATE_ACCESS_ROLE_REQUEST.rawJson)
            }
        )

        val conversationApi = ConversationApiV0(networkClient)
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

        val conversationApi = ConversationApiV0(networkClient)
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

        val conversationApi = ConversationApiV0(networkClient)
        conversationApi.updateAccess(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUpdated>>(it)
        }
    }

    @Test
    fun givenSuccessAccessUpdateResponseWithDeprecatedAccessRoleField_whenUpdatingAccessRole_thenAccessUpdateEventIsPropagated() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE, ConversationAccessDTO.INVITE), setOf()
        )
        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validAccessUpdateWithDeprecatedAccessRoleField.rawJson, statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiV0(networkClient)
        conversationApi.updateAccess(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUpdated>>(it)
        }
    }

    @Test
    fun givenResponseWithNullAccessRole_whenUpdatingAccessRole_thenAccessUpdateEventIsPropagated() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE, ConversationAccessDTO.INVITE), setOf()
        )
        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validNullAccessRole, statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiV0(networkClient)
        conversationApi.updateAccess(ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"), accessRoles).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUpdated>>(it)
            assertEquals(ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL, it.value.event.data.accessRole)
        }
    }

    @Test
    fun whenUpdatingMemberRole_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val userId = UserId("userId", "userDomain")
        val memberRole = ConversationMemberRoleDTO("conversation_role")
        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPut()
                assertPathEqual("/conversations/conversationDomain/conversationId/members/userDomain/userId")
            }
        )

        val conversationApi = ConversationApiV0(networkClient)
        conversationApi.updateConversationMemberRole(conversationId, userId, memberRole)
    }

    @Test
    fun whenAddingMemberToGroup_thenTheMemberShouldBeAddedCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val userId = UserId("userId", "userDomain")
        val request = AddConversationMembersRequest(listOf(userId), "Member")

        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validMemberJoin.rawJson, statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_MEMBERS/$PATH_V2")
            }
        )
        val conversationApi = ConversationApiV0(networkClient)
        val response = conversationApi.addMember(request, conversationId)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenServiceId_whenAddingToGroup_thenRequestShouldMeetTheSpec() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val serviceId = AddServiceRequest("service_id", "service_provider")

        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = AddServiceResponseJson.valid,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertPathEqual("conversations/${conversationId.value}/bots")
                assertNoQueryParams()
            }
        )
        val conversationApi = ConversationApiV0(networkClient)
        val response = conversationApi.addService(serviceId, conversationId)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun whenRemovingMemberFromGroup_thenTheMemberShouldBeRemovedCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val userId = UserId("userId", "userDomain")

        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validMemberLeave.rawJson, statusCode = HttpStatusCode.OK,
            assertion = {
                assertDelete()
                assertPathEqual(
                    "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS/${userId.domain}/${userId.value}"
                )
            }
        )
        val conversationApi = ConversationApiV0(networkClient)
        val response = conversationApi.removeMember(userId, conversationId)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun whenUpdatingConversationName_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPut()
                assertPathEqual("/conversations/conversationDomain/conversationId/name")
            }
        )

        val conversationApi = ConversationApiV0(networkClient)
        val response = conversationApi.updateConversationName(conversationId, "new_name")

        assertTrue(response.isSuccessful())
    }

    @Test
    fun whenJoiningConversationViaCode_whenResponseWith200_thenEventIsParsedCorrectly() = runTest {
        val request = JoinConversationRequestV0("code", "key", null)

        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validMemberJoin.rawJson, statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("$PATH_CONVERSATIONS/$PATH_JOIN")
            }
        )
        val conversationApi = ConversationApiV0(networkClient)
        val response = conversationApi.joinConversation(request.code, request.key, request.uri, null)

        assertIs<NetworkResponse.Success<ConversationMemberAddedResponse>>(response)
        assertIs<ConversationMemberAddedResponse.Changed>(response.value)
        assertEquals(
            EventContentDTOJson.validMemberJoin.serializableData,
            (response.value as ConversationMemberAddedResponse.Changed).event
        )
    }

    @Test
    fun whenJoiningConversationViaCode_whenResponseWith204_thenEventIsParsedCorrectly() = runTest {
        val request = JoinConversationRequestV0("code", "key", null)

        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPost()
                assertPathEqual("$PATH_CONVERSATIONS/$PATH_JOIN")
            }
        )
        val conversationApi = ConversationApiV0(networkClient)
        val response = conversationApi.joinConversation(request.code, request.key, request.uri, null)

        assertIs<NetworkResponse.Success<ConversationMemberAddedResponse>>(response)
        assertIs<ConversationMemberAddedResponse.Unchanged>(response.value)
    }

    @Test
    fun givenReceiptMode_whenUpdatingConversationReceiptMode_thenRequestIsConfiguredCorrectly() = runTest {
        // given
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val receiptMode = ConversationReceiptModeDTO(receiptMode = ReceiptMode.ENABLED)

        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validUpdateReceiptMode.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPut()
                assertPathEqual("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/${PATH_RECEIPT_MODE}")
            }
        )

        val conversationApi = ConversationApiV0(networkClient)

        // when
        val response = conversationApi.updateReceiptMode(conversationId, receiptMode)

        // then
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenRightAccess_whenGeneratingGuestRoomLink_thenRequestIsConfiguredCorrectly() = runTest {
        // given
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val networkClient = mockAuthenticatedNetworkClient(
            validGenerateGuestRoomLink,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertPathEqual("$PATH_CONVERSATIONS/${conversationId.value}/${PATH_CODE}")
            }
        )

        val conversationApi = ConversationApiV0(networkClient)

        // when
        val response = conversationApi.generateGuestRoomLink(conversationId, null)

        // then
        assertIs<NetworkResponse.Success<EventContentDTO.Conversation.CodeUpdated>>(response)
    }

    @Test
    fun givenRightAccess_whenRevokingGuestRoomLink_thenRequestIsConfiguredCorrectly() = runTest {
        // given
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertDelete()
                assertPathEqual("$PATH_CONVERSATIONS/${conversationId.value}/${PATH_CODE}")
            }
        )

        val conversationApi = ConversationApiV0(networkClient)

        // when
        val response = conversationApi.revokeGuestRoomLink(conversationId)

        // then
        assertIs<NetworkResponse.Success<Unit>>(response)
    }

    @Test
    fun givenTypingNotificationRequest_whenSendingStatus_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        // given
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val request = TypingIndicatorStatusDTO(TypingIndicatorStatus.STOPPED)

        val networkClient = mockAuthenticatedNetworkClient(
            ByteArray(0),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("${PATH_CONVERSATIONS}/${conversationId.value}/${PATH_TYPING_NOTIFICATION}")
                assertJsonBodyContent(SendTypingStatusNotificationRequestJson.createValid(TypingIndicatorStatus.STOPPED).rawJson)
            }
        )
        val conversationApi = ConversationApiV0(networkClient)

        // when
        val response = conversationApi.sendTypingIndicatorNotification(conversationId, request)

        // then
        assertIs<NetworkResponse.Success<Unit>>(response)
    }

    @Test
    fun givenRequest_whenFetchingMlsOneToOneConversation_thenRequestShouldFail() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(responseBody = "", statusCode = HttpStatusCode.OK)
        val conversationApi = ConversationApiV0(networkClient)
        val response = conversationApi.fetchMlsOneToOneConversation(UserId("domain", "id"))

        assertFalse(response.isSuccessful())
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_CONVERSATIONS_LIST_V2 = "/conversations/list/v2"
        const val PATH_CONVERSATIONS_IDS = "/conversations/list-ids"
        const val PATH_SELF = "/self"
        const val PATH_MEMBERS = "members"
        const val PATH_V2 = "v2"
        const val PATH_JOIN = "join"
        const val PATH_RECEIPT_MODE = "receipt-mode"
        const val PATH_CODE = "code"
        const val PATH_TYPING_NOTIFICATION = "typing"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.v0().rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.v0
        val CREATE_CONVERSATION_IDS_REQUEST = ConversationListIdsResponseJson.validRequestIds
        val UPDATE_ACCESS_ROLE_REQUEST = UpdateConversationAccessRequestJson.v0
        val CONVERSATION_IDS_RESPONSE = ConversationListIdsResponseJson.validGetIds
        val CONVERSATION_DETAILS_RESPONSE = ConversationDetailsResponse.validGetDetailsForIds
        val MEMBER_UPDATE_REQUEST = MemberUpdateRequestJson.valid
    }
}
