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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol.MLS
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.authenticated.conversation.guestroomlink.GenerateGuestRoomLinkResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.LimitedConversationInfo
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationViewEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.eq
import io.mockative.fun2
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.wire.kalium.persistence.dao.Member as MemberEntity

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationGroupRepositoryTest {

    @Test
    fun givenSelfUserBelongsToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPI(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withInsertMembersWithQualifiedIdSucceeds()
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasInvoked(once)

            verify(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .with(anything(), anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenSelfUserDoesNotBelongToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPI(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withInsertMembersWithQualifiedIdSucceeds()
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasInvoked(once)

            verify(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .with(anything(), anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPI(NetworkResponse.Success(conversationResponse, emptyMap(), 201))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withMlsConversationEstablished()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasInvoked(once)

            verify(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .with(anything(), anything())
                .wasInvoked(once)

            verify(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .with(anything(), anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenAConversationAndAPISucceedsWithChange_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedChanged()
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithChange_whenAddingServiceToConversation_thenShouldSucceed() = runTest {
        val serviceID = ServiceId("service-id", "service-provider")
        val addServiceRequest = AddServiceRequest(id = serviceID.id, provider = serviceID.provider)

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddServiceAPISucceedChanged()
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.addService(serviceID, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::addService)
            .with(eq(addServiceRequest), eq(TestConversation.ID.toApi()))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSConversation_whenAddingServiceToConversation_theReturnError() = runTest {
        val serviceID = ServiceId("service-id", "service-provider")

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .arrange()

        conversationGroupRepository.addService(serviceID, TestConversation.ID)
            .shouldFail {
                assertIs<MLSFailure>(it)
                assertIs<UnsupportedOperationException>(it.exception)
            }

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::addService)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationAndAPISucceedsWithoutChange_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedUnchanged()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationAndAPIFailed_whenAddingMembersToConversation_thenShouldNotSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withAddMemberAPIFailed()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldFail()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenAddMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        // this is called in the mlsRepo
        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithChange_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulHandleMemberLeaveEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithoutChange_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedUnchanged()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationAndAPIFailed_whenRemovingMemberFromConversation_thenShouldFail() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPIFailed()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldFail()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenRemovingLeavingConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulLeaveMLSGroup()
            .withSuccessfulHandleMemberLeaveEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestUser.SELF.id, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::leaveGroup)
            .with(eq(GROUP_ID))
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenRemoveMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulRemoveMemberFromMLSGroup()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        // this function called in the mlsRepo
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasNotInvoked()
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::leaveGroup)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenProteusConversation_whenJoiningConversationSuccessWithChanged_thenResponseIsHandled() = runTest {
        val (code, key, uri) = Triple("code", "key", null)

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withJoinConversationAPIResponse(
                code,
                key,
                uri,
                NetworkResponse.Success(ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE, emptyMap(), 200)
            )
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.joinViaInviteCode(code, key, uri)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::joinConversation)
            .with(eq(code), eq(key), eq(uri))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConversation_whenJoiningConversationSuccessWithChanged_thenAddSelfClientsToMlsGroup() = runTest {
        val (code, key, uri) = Triple("code", "key", null)

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withJoinConversationAPIResponse(
                code,
                key,
                uri,
                NetworkResponse.Success(ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE, emptyMap(), 200)
            )
            .withSuccessfulHandleMemberJoinEvent()
            .withJoinExistingMlsConversationSucceeds()
            .withSuccessfulAddMemberToMLSGroup()
            .arrange()

        conversationGroupRepository.joinViaInviteCode(code, key, uri)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::joinConversation)
            .with(eq(code), eq(key), eq(uri))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.joinExistingMLSConversation)
            .suspendFunction(arrangement.joinExistingMLSConversation::invoke)
            .with(eq(ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE.event.qualifiedConversation.toModel()))
            .wasInvoked(exactly = once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(GroupID(MLS_PROTOCOL_INFO.groupId)), eq(listOf(TestUser.SELF.id)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusConversation_whenJoiningConversationSuccessWithUnchanged_thenMemberJoinEventHandlerIsNotInvoked() = runTest {
        val (code, key, uri) = Triple("code", "key", null)

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withJoinConversationAPIResponse(
                code,
                key,
                uri,
                NetworkResponse.Success(ConversationMemberAddedResponse.Unchanged, emptyMap(), 204)
            )
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.joinViaInviteCode(code, key, uri)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::joinConversation)
            .with(eq(code), eq(key), eq(uri))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenCodeAndKey_whenFetchingLimitedConversationInfo_thenApiIsCalled() = runTest {
        val (code, key) = "code" to "key"

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFetchLimitedConversationInfo(
                code,
                key,
                NetworkResponse.Success(TestConversation.LIMITED_CONVERSATION_INFO, emptyMap(), 200)
            )
            .arrange()

        conversationGroupRepository.fetchLimitedInfoViaInviteCode(code, key)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::fetchLimitedInformationViaCode)
            .with(eq(code), eq(key))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASuccessApiCall_whenTryingToGenerateANewGuestRoomLink_ThenCallUpdateGuestLinkInDB() = runTest {
        val conversationId = ConversationId("value", "domain")
        val link = "www.wire.com"
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withSuccessfulCallToGenerateGuestRoomLinkApi()
            .withSuccessfulUpdateOfGuestRoomLinkInDB(link)
            .arrange()

        val result = conversationGroupRepository.generateGuestRoomLink(conversationId)

        result.shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::generateGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateGuestRoomLink)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAFailedApiCall_whenTryingToGenerateANewGuestRoomLink_ThenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFailedCallToGenerateGuestRoomLinkApi()
            .arrange()

        val result = conversationGroupRepository.generateGuestRoomLink(conversationId)

        result.shouldFail()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::generateGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateGuestRoomLink)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenASuccessApiCall_whenTryingToRevokeGuestRoomLink_ThenCallUpdateGuestLinkInDB() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withSuccessfulCallToRevokeGuestRoomLinkApi()
            .withSuccessfulUpdateOfGuestRoomLinkInDB(null)
            .arrange()

        val result = conversationGroupRepository.revokeGuestRoomLink(conversationId)

        result.shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::revokeGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateGuestRoomLink)
            .with(any(), eq(null))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAFailedApiCall_whenTryingToRevokingGuestRoomLink_ThenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFailedCallToRevokeGuestRoomLinkApi()
            .arrange()

        val result = conversationGroupRepository.revokeGuestRoomLink(conversationId)

        result.shouldFail()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::revokeGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateGuestRoomLink)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenDaoRunsEmitsValues_whenObservingGuestRoomLink_thenPropagateGuestRoomLink() = runTest {
        val conversationId = ConversationId("value", "domain")

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withSuccessfulFetchOfGuestRoomLink()
            .arrange()

        val result = conversationGroupRepository.observeGuestRoomLink(conversationId)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::observeGuestRoomLinkByConversationId)
            .with(any())
            .wasInvoked(exactly = once)
        assertEquals(LINK, result.first())
    }

    private class Arrangement {

        @Mock
        val memberJoinEventHandler = mock(MemberJoinEventHandler::class)

        @Mock
        val memberLeaveEventHandler = mock(MemberLeaveEventHandler::class)

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        @Mock
        val mlsConversationRepository: MLSConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val conversationDAO: ConversationDAO = mock(ConversationDAO::class)

        @Mock
        val conversationApi: ConversationApi = mock(ConversationApi::class)

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val newConversationMembersRepository = mock(NewConversationMembersRepository::class)

        @Mock
        val newGroupConversationStartedMessageCreator = mock(NewGroupConversationStartedMessageCreator::class)

        @Mock
        val joinExistingMLSConversation: JoinExistingMLSConversationUseCase = mock(JoinExistingMLSConversationUseCase::class)

        val conversationGroupRepository =
            ConversationGroupRepositoryImpl(
                mlsConversationRepository,
                joinExistingMLSConversation,
                memberJoinEventHandler,
                memberLeaveEventHandler,
                conversationDAO,
                conversationApi,
                newConversationMembersRepository,
                newGroupConversationStartedMessageCreator,
                TestUser.SELF.id,
                selfTeamIdProvider
            )

        fun withMlsConversationEstablished(): Arrangement {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withInsertMembersWithQualifiedIdSucceeds(): Arrangement {
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
                .whenInvokedWith(any(), any())
                .thenDoNothing()
            return this
        }

        fun withCreateNewConversationAPI(result: NetworkResponse<ConversationResponse>): Arrangement = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::createNewConversation)
                .whenInvokedWith(anything())
                .thenReturn(result)
        }

        fun withSelfTeamId(result: Either<StorageFailure, TeamId?>): Arrangement = apply {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withInsertConversationSuccess(): Arrangement = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
        }

        fun withFetchLimitedConversationInfo(
            code: String,
            key: String,
            result: NetworkResponse<LimitedConversationInfo>
        ): Arrangement = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchLimitedInformationViaCode)
                .whenInvokedWith(eq(code), eq(key))
                .thenReturn(result)
        }

        fun withJoinConversationAPIResponse(
            code: String,
            key: String,
            uri: String?,
            result: NetworkResponse<ConversationMemberAddedResponse>
        ): Arrangement = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::joinConversation)
                .whenInvokedWith(eq(code), eq(key), eq(uri))
                .thenReturn(result)
        }

        fun withJoinExistingMlsConversationSucceeds() = apply {
            given(joinExistingMLSConversation)
                .suspendFunction(joinExistingMLSConversation::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withConversationDetailsById(conversation: Conversation) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::baseInfoById)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(conversation))
        }

        fun withConversationDetailsById(result: ConversationViewEntity?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withProtocolInfoById(result: ConversationEntity.ProtocolInfo) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationProtocolInfo)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withAddMemberAPISucceedChanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::addMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withAddServiceAPISucceedChanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::addService)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        TestConversation.ADD_SERVICE_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withAddMemberAPISucceedUnchanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::addMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        ConversationMemberAddedResponse.Unchanged,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withAddMemberAPIFailed() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::addMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )
        }

        fun withDeleteMemberAPISucceedChanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        TestConversation.REMOVE_MEMBER_FROM_CONVERSATION_SUCCESSFUL_RESPONSE,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withDeleteMemberAPISucceedUnchanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        ConversationMemberRemovedResponse.Unchanged,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withDeleteMemberAPIFailed() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )
        }

        fun withFetchUsersIfUnknownByIdsSuccessful() = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulMemberDeletion() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteMemberByQualifiedID)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withSuccessfulHandleMemberJoinEvent() = apply {
            given(memberJoinEventHandler)
                .suspendFunction(memberJoinEventHandler::handle)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulHandleMemberLeaveEvent() = apply {
            given(memberLeaveEventHandler)
                .suspendFunction(memberLeaveEventHandler::handle)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulLeaveMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::leaveGroup)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulAddMemberToMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::addMemberToMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulRemoveMemberFromMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::removeMembersFromMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulCallToGenerateGuestRoomLinkApi() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::generateGuestRoomLink)
                .whenInvokedWith(any())
                .thenReturn(
                    NetworkResponse.Success(
                        GenerateGuestRoomLinkResponse(uri = "mock-guest-room-link"),
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withFailedCallToGenerateGuestRoomLinkApi() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::generateGuestRoomLink)
                .whenInvokedWith(any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )
        }

        fun withSuccessfulUpdateOfGuestRoomLinkInDB(link: String?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateGuestRoomLink)
                .whenInvokedWith(any(), eq(link))
                .thenReturn(Unit)
        }

        fun withSuccessfulCallToRevokeGuestRoomLinkApi() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::revokeGuestRoomLink)
                .whenInvokedWith(any())
                .thenReturn(
                    NetworkResponse.Success(
                        Unit,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withFailedCallToRevokeGuestRoomLinkApi() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::revokeGuestRoomLink)
                .whenInvokedWith(any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )
        }

        fun withSuccessfulFetchOfGuestRoomLink() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeGuestRoomLinkByConversationId)
                .whenInvokedWith(any())
                .thenReturn(GUEST_ROOM_LINK_FLOW)
        }

        fun withSuccessfulNewConversationGroupStartedHandled() = apply {
            given(newGroupConversationStartedMessageCreator)
                .suspendFunction(newGroupConversationStartedMessageCreator::createSystemMessage)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulNewConversationMemberHandled() = apply {
            given(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to conversationGroupRepository
    }

    companion object {
        private const val RAW_GROUP_ID = "mlsGroupId"
        const val LINK = "www.wire.com"
        private val GUEST_ROOM_LINK_FLOW = flowOf(LINK)
        val GROUP_ID = GroupID(RAW_GROUP_ID)
        val PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus
        val MLS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo
            .MLS(
                RAW_GROUP_ID,
                groupState = ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )

        const val GROUP_NAME = "Group Name"

        val USER_ID = TestUser.USER_ID
        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            ConversationMembersResponse(
                ConversationMemberDTO.Self(TestUser.SELF.id.toApi(), "wire_member"),
                emptyList()
            ),
            GROUP_NAME,
            TestConversation.NETWORK_ID,
            null,
            0UL,
            ConversationResponse.Type.GROUP,
            0,
            null,
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z",
            access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
            accessRole = setOf(
                ConversationAccessRoleDTO.GUEST,
                ConversationAccessRoleDTO.TEAM_MEMBER,
                ConversationAccessRoleDTO.NON_TEAM_MEMBER
            ),
            mlsCipherSuiteTag = null,
            receiptMode = ReceiptMode.DISABLED
        )
    }
}
