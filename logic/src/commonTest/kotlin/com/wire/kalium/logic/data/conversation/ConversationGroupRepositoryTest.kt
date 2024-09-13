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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.thenReturnSequentially
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
import com.wire.kalium.network.api.base.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import com.wire.kalium.network.api.base.authenticated.conversation.messagetimer.ConversationMessageTimerDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.FederationUnreachableResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationGuestLinkEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.eq
import io.mockative.fun1
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("LargeClass")
class ConversationGroupRepositoryTest {

    @Test
    fun givenSelfUserBelongsToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
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
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
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
    fun givenCreatingAGroupConversation_whenThereIsAnUnreachableError_thenRetryIsExecutedWithValidUsersOnly() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(
                arrayOf(
                    FEDERATION_ERROR_UNREACHABLE_DOMAINS,
                    NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)
                )
            )
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .arrange()

        val unreachableUserId = TestUser.USER_ID.copy(domain = "unstableDomain2.com")
        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID, unreachableUserId),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verify(conversationApi)
                .suspendFunction(conversationApi::createNewConversation)
                .with(matching { it.qualifiedUsers?.size == 2 })
                .wasInvoked(once)

            verify(conversationApi)
                .suspendFunction(conversationApi::createNewConversation)
                .with(matching { it.qualifiedUsers?.size == 1 })
                .wasInvoked(once)

            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasInvoked(once)

            verify(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .with(anything(), anything(), eq(listOf(unreachableUserId)))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenCreatingAGroupConversation_whenThereIsAnUnreachableError_thenRetryIsExecutedWithValidUsersOnlyOnce() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(
                arrayOf(
                    FEDERATION_ERROR_UNREACHABLE_DOMAINS,
                    FEDERATION_ERROR_UNREACHABLE_DOMAINS,
                )
            )
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .arrange()

        val unreachableUserId = TestUser.USER_ID.copy(domain = "unstableDomain2.com")
        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID, unreachableUserId),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldFail()

        with(arrangement) {
            verify(conversationApi)
                .suspendFunction(conversationApi::createNewConversation)
                .with(any())
                .wasInvoked(twice)

            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasNotInvoked()

            verify(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .with(anything(), anything())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(conversationResponse, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withMlsConversationEstablished(MLSAdditionResult(setOf(TestUser.USER_ID), emptySet()))
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
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
                .with(anything(), anything(), anything(), eq(true))
                .wasInvoked(once)

            verify(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .with(anything(), anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenMLSProtocolIsUsedAndSomeUsersAreNotAddedToMLSGroup_whenCallingCreateGroupConversation_thenMissingMembersArePersisted() =
        runTest {
            val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)
            val missingMembersFromMLSGroup = setOf(TestUser.OTHER_USER_ID, TestUser.OTHER_USER_ID_2)
            val successfullyAddedUsers = setOf(TestUser.USER_ID)
            val allWantedMembers = successfullyAddedUsers + missingMembersFromMLSGroup
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(conversationResponse, emptyMap(), 201)))
                .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
                .withInsertConversationSuccess()
                .withMlsConversationEstablished(MLSAdditionResult(setOf(TestUser.USER_ID), notAddedUsers = missingMembersFromMLSGroup))
                .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
                .withSuccessfulNewConversationGroupStartedHandled()
                .withSuccessfulNewConversationMemberHandled()
                .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
                .arrange()

            val result = conversationGroupRepository.createGroupConversation(
                GROUP_NAME,
                allWantedMembers.toList(),
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
                    .with(anything(), anything(), anything(), eq(true))
                    .wasInvoked(once)

                verify(newConversationMembersRepository)
                    .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                    .with(anything(), anything(), eq(missingMembersFromMLSGroup.toList()))
                    .wasInvoked(once)
            }
        }

    @Test
    fun givenProteusConversation_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedChanged()
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::addMember)
            .with(anything(), eq(TestConversation.ID.toApi()))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusConversation_whenAddingServiceToConversation_thenShouldSucceed() = runTest {
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
    fun givenProteusConversationAndUserIsAlreadyAMember_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
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
    fun givenProteusConversationAndAPICallFails_whenAddingMembersToConversation_thenShouldFail() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withAddMemberAPIFailed()
            .withInsertFailedToAddSystemMessageSuccess()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldFail()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenAddingServiceToConversation_thenReturnError() = runTest {
        val serviceID = ServiceId("service-id", "service-provider")

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .arrange()

        conversationGroupRepository.addService(serviceID, TestConversation.ID)
            .shouldFail {
                assertIs<MLSFailure.Generic>(it)
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
            .withInsertFailedToAddSystemMessageSuccess()
            .withAddMemberAPIFailed()
            .arrange()

        val expectedInitialUsers = listOf(TestConversation.USER_1, TestConversation.USER_2)
        conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID)
            .shouldFail()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()

        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
            .with(anything(), matching {
                it.containsAll(expectedInitialUsers)
            })
            .wasInvoked(once)
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenAddMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .withInsertFailedToAddSystemMessageSuccess()
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
    fun givenMixedConversation_whenAddMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MIXED_CONVERSATION)
            .withProtocolInfoById(MIXED_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .withSuccessfulHandleMemberJoinEvent()
            .withInsertAddedAndFailedSystemMessageSuccess()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::addMember)
            .with(anything(), eq(TestConversation.ID.toApi()))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusConversation_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulHandleMemberLeaveEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::removeMember)
            .with(eq(TestConversation.USER_1.toApi()), eq(TestConversation.ID.toApi()))
            .wasInvoked(exactly = once)

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusConversationAndUserIsNotAMember_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
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
    fun givenProteusConversationAndAPICallFails_whenRemovingMemberFromConversation_thenShouldFail() = runTest {
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
    fun givenMLSConversation_whenRemovingLeavingConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
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
    fun givenMLSConversation_whenRemoveMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulRemoveMemberFromMLSGroup()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

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
    fun givenMixedConversation_whenRemoveMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MIXED_CONVERSATION)
            .withProtocolInfoById(MIXED_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulRemoveMemberFromMLSGroup()
            .withSuccessfulHandleMemberLeaveEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::removeMember)
            .with(eq(TestConversation.USER_1.toApi()), eq(TestConversation.ID.toApi()))
            .wasInvoked(exactly = once)

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusConversation_whenJoiningConversationSuccessWithChanged_thenResponseIsHandled() = runTest {
        val code = "code"
        val key = "key"
        val uri = null
        val password = null

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

        conversationGroupRepository.joinViaInviteCode(code, key, uri, password)
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
    fun givenProteusConversation_whenJoiningConversationSuccessWithUnchanged_thenMemberJoinEventHandlerIsNotInvoked() = runTest {
        val code = "code"
        val key = "key"
        val uri = null
        val password = null

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

        conversationGroupRepository.joinViaInviteCode(code, key, uri, password)
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
    fun givenMLSConversation_whenJoiningConversationSuccessWithChanged_thenAddSelfClientsToMlsGroup() = runTest {
        val code = "code"
        val key = "key"
        val uri = null
        val password = null

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

        conversationGroupRepository.joinViaInviteCode(code, key, uri, password)
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
    fun givenMixedConversation_whenJoiningConversationSuccessWithChanged_thenAddSelfClientsToMlsGroup() = runTest {
        val code = "code"
        val key = "key"
        val uri = null
        val password = null

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(MIXED_PROTOCOL_INFO)
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

        conversationGroupRepository.joinViaInviteCode(code, key, uri, password)
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
            .with(eq(GroupID(MIXED_PROTOCOL_INFO.groupId)), eq(listOf(TestUser.SELF.id)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCodeAndKey_whenFetchingLimitedConversationInfo_thenApiIsCalled() = runTest {
        val (code, key) = "code" to "key"

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFetchLimitedConversationInfo(
                code,
                key,
                NetworkResponse.Success(TestConversation.CONVERSATION_CODE_INFO, emptyMap(), 200)
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

        val expected = EventContentDTO.Conversation.CodeUpdated(
            qualifiedConversation = conversationId.toApi(),
            data = ConversationInviteLinkResponse(
                uri = link,
                code = "code",
                key = "key",
                hasPassword = false
            ),
            qualifiedFrom = TestUser.USER_ID.toApi()
        )
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withSuccessfulCallToGenerateGuestRoomLinkApi(expected)
            .arrange()

        val result = conversationGroupRepository.generateGuestRoomLink(conversationId, null)

        result.shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::generateGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateGuestRoomLink)
            .with(any(), anything(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAFailedApiCall_whenTryingToGenerateANewGuestRoomLink_ThenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFailedCallToGenerateGuestRoomLinkApi()
            .arrange()

        val result = conversationGroupRepository.generateGuestRoomLink(conversationId, null)

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
    fun givenPassword_whenTryingToGenerateANewGuestRoomLink_ThenCallUpdateGuestLinkInDB() = runTest {
        val conversationId = ConversationId("value", "domain")
        val link = "www.wire.com"

        val expected = EventContentDTO.Conversation.CodeUpdated(
            qualifiedConversation = conversationId.toApi(),
            data = ConversationInviteLinkResponse(
                uri = link,
                code = "code",
                key = "key",
                hasPassword = true
            ),
            qualifiedFrom = TestUser.USER_ID.toApi()
        )

        val expectedPassword = "password"
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withSuccessfulCallToGenerateGuestRoomLinkApi(expected)
            .arrange()

        val result = conversationGroupRepository.generateGuestRoomLink(conversationId, expectedPassword)

        result.shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::generateGuestRoomLink)
            .with(any(), eq(expectedPassword))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateGuestRoomLink)
            .with(any(), anything(), any())
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

        val expected = ConversationGuestLinkEntity(
            link = "link",
            isPasswordProtected = false
        )

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withSuccessfulFetchOfGuestRoomLink(flowOf(expected))
            .arrange()

        val result = conversationGroupRepository.observeGuestRoomLink(conversationId)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::observeGuestRoomLinkByConversationId)
            .with(any())
            .wasInvoked(exactly = once)
        result.first().shouldSucceed {
            assertEquals(expected.link, it?.link)
            assertEquals(expected.isPasswordProtected, it?.isPasswordProtected)
        }
    }

    @Test
    fun givenAConversationAndAPISucceeds_whenUpdatingMessageTimer_thenShouldTriggerHandler() = runTest {
        // given
        val messageTimer = 5000L
        val messageTimerUpdateEvent = EventContentDTO.Conversation.MessageTimerUpdate(
            TestConversation.NETWORK_ID,
            ConversationMessageTimerDTO(messageTimer),
            TestConversation.NETWORK_USER_ID1,
            "2022-03-30T15:36:00.000Z"
        )

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withUpdateMessageTimerAPISuccess(messageTimerUpdateEvent)
            .withSuccessfulHandleMessageTimerUpdateEvent()
            .arrange()

        // when
        val result = conversationGroupRepository.updateMessageTimer(
            TestConversation.ID,
            messageTimer
        )

        // then
        result.shouldSucceed()

        verify(arrangement.conversationMessageTimerEventHandler)
            .suspendFunction(arrangement.conversationMessageTimerEventHandler::handle)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPIFailed_whenUpdatingMessageTimer_thenShouldNotTriggerHandler() = runTest {
        // given
        val messageTimer = 5000L

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withUpdateMessageTimerAPIFailed()
            .withSuccessfulHandleMessageTimerUpdateEvent()
            .arrange()

        // when
        val result = conversationGroupRepository.updateMessageTimer(
            TestConversation.ID,
            messageTimer
        )

        // then
        result.shouldFail()

        verify(arrangement.conversationMessageTimerEventHandler)
            .suspendFunction(arrangement.conversationMessageTimerEventHandler::handle)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationAndAPIFailsWithUnreachableDomains_whenAddingMembersToConversation_thenShouldRetryWithValidUsers() =
        runTest {
            val failedDomain = "unstableDomain1.com"
            // given
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withConversationDetailsById(TestConversation.CONVERSATION)
                .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
                .withFetchUsersIfUnknownByIdsSuccessful()
                .withAddMemberAPIFailsFirstWithUnreachableThenSucceed(
                    arrayOf(FEDERATION_ERROR_UNREACHABLE_DOMAINS, API_SUCCESS_MEMBER_ADDED)
                )
                .withSuccessfulHandleMemberJoinEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsers = listOf(
                TestConversation.USER_1.copy(domain = failedDomain), TestUser.OTHER_FEDERATED_USER_ID
            )
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldSucceed()

            // then
            val expectedFullUserIdsForRequestCount = 2
            val expectedValidUsersCount = 1
            verify(arrangement.conversationApi)
                .suspendFunction(arrangement.conversationApi::addMember)
                .with(matching {
                    it.users.size == expectedFullUserIdsForRequestCount
                }).wasInvoked(exactly = once)

            verify(arrangement.conversationApi)
                .suspendFunction(arrangement.conversationApi::addMember)
                .with(matching {
                    it.users.size == expectedValidUsersCount && it.users.first().domain != failedDomain
                }).wasInvoked(exactly = once)

            verify(arrangement.memberJoinEventHandler)
                .suspendFunction(arrangement.memberJoinEventHandler::handle)
                .with(anything())
                .wasInvoked(exactly = once)

            verify(arrangement.newGroupConversationSystemMessagesCreator)
                .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
                .with(anything(), matching { it.size == expectedValidUsersCount })
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenAConversationAndAPIFailsWithUnreachableDomains_whenAddingMembersToConversation__thenRetryIsExecutedWithValidUsersOnlyOnce() =
        runTest {
            val failedDomain = "unstableDomain1.com"
            // given
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withConversationDetailsById(TestConversation.CONVERSATION)
                .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
                .withFetchUsersIfUnknownByIdsSuccessful()
                .withAddMemberAPIFailsFirstWithUnreachableThenSucceed(
                    arrayOf(FEDERATION_ERROR_UNREACHABLE_DOMAINS, FEDERATION_ERROR_UNREACHABLE_DOMAINS)
                )
                .withSuccessfulHandleMemberJoinEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsers = listOf(TestConversation.USER_1.copy(domain = failedDomain), TestUser.OTHER_FEDERATED_USER_ID)
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldFail()

            // then
            verify(arrangement.conversationApi)
                .suspendFunction(arrangement.conversationApi::addMember)
                .with(anything())
                .wasInvoked(exactly = twice)

            verify(arrangement.memberJoinEventHandler)
                .suspendFunction(arrangement.memberJoinEventHandler::handle)
                .with(anything())
                .wasNotInvoked()

            verify(arrangement.newGroupConversationSystemMessagesCreator)
                .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
                .with(anything(), matching {
                    it.containsAll(expectedInitialUsers)
                })
                .wasInvoked(once)
        }

    @Test
    fun givenAConversationFailsWithUnreachableAndNotFromUsersInRequest_whenAddingMembers_thenRetryIsNotExecutedAndCreateSysMessage() =
        runTest {
            // given
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withConversationDetailsById(TestConversation.CONVERSATION)
                .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
                .withFetchUsersIfUnknownByIdsSuccessful()
                .withAddMemberAPIFailsFirstWithUnreachableThenSucceed(
                    arrayOf(FEDERATION_ERROR_UNREACHABLE_DOMAINS, FEDERATION_ERROR_UNREACHABLE_DOMAINS)
                )
                .withSuccessfulHandleMemberJoinEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsersNotFromUnreachableInformed = listOf(TestConversation.USER_1)
            conversationGroupRepository.addMembers(expectedInitialUsersNotFromUnreachableInformed, TestConversation.ID).shouldFail()

            // then
            verify(arrangement.conversationApi)
                .suspendFunction(arrangement.conversationApi::addMember)
                .with(anything())
                .wasInvoked(exactly = once)

            verify(arrangement.memberJoinEventHandler)
                .suspendFunction(arrangement.memberJoinEventHandler::handle)
                .with(anything())
                .wasNotInvoked()

            verify(arrangement.newGroupConversationSystemMessagesCreator)
                .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
                .with(anything(), matching {
                    it.containsAll(expectedInitialUsersNotFromUnreachableInformed)
                })
                .wasInvoked(once)
        }

    @Test
    fun givenAValidConversation_whenCreating_thenConversationIsCreatedAndUnverifiedWarningSystemMessagePersisted() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
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

            verify(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationStartedUnverifiedWarning)
                .with(anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenNoPackagesAvailable_whenAddingMembers_thenRetryOnceWithValidUsersAndPersistSystemMessage() = runTest {
        // given
        val expectedInitialUsers = listOf(TestConversation.USER_1, TestUser.OTHER_FEDERATED_USER_ID)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .withAddingMemberToMlsGroupResults(
                KEY_PACKAGES_NOT_AVAILABLE_FAILURE,
                Either.Right(Unit)
            )
            .withInsertFailedToAddSystemMessageSuccess()
            .arrange()

        // when
        conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldSucceed()

        // then
        val expectedFullUserIdsForRequestCount = 2
        val expectedValidUsersWithKeyPackagesCount = 1
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(anything(), matching {
                it.size == expectedFullUserIdsForRequestCount
            }).wasInvoked(exactly = once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(anything(), matching {
                it.size == expectedValidUsersWithKeyPackagesCount && it.first() == TestConversation.USER_1
            }).wasInvoked(exactly = once)

        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
            .with(anything(), matching { it.size == 1 })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSendCommitFederatedFailure_whenAddingMembers_thenRetryOnceWithValidUsersAndPersistSystemMessage() = runTest {
        // given
        val expectedInitialUsers = listOf(TestConversation.USER_1, TestUser.OTHER_FEDERATED_USER_ID)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .withAddingMemberToMlsGroupResults(
                buildCommitBundleFederatedFailure("otherDomain"),
                Either.Right(Unit)
            )
            .withInsertFailedToAddSystemMessageSuccess()
            .arrange()

        // when
        conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldSucceed()

        // then
        val expectedFullUserIdsForRequestCount = 2
        val expectedValidUsersWithKeyPackagesCount = 1
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(anything(), matching {
                it.size == expectedFullUserIdsForRequestCount
            }).wasInvoked(exactly = once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(anything(), matching {
                it.size == expectedValidUsersWithKeyPackagesCount && it.first() == TestConversation.USER_1
            }).wasInvoked(exactly = once)

        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
            .with(anything(), matching { it.size == 1 })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMoreThan2Retries_whenAddingMembers_thenTheOperationsShouldFailAndPersistFailedToAddAllMembers() = runTest {
        // given
        val expectedInitialUsers = listOf(TestConversation.USER_1, TestUser.OTHER_FEDERATED_USER_ID_2, TestUser.OTHER_FEDERATED_USER_ID)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withAddingMemberToMlsGroupResults(
                KEY_PACKAGES_NOT_AVAILABLE_FAILURE,
                buildCommitBundleFederatedFailure("otherDomain2"),
                buildCommitBundleFederatedFailure("domainMember"),
            )
            .withInsertFailedToAddSystemMessageSuccess()
            .arrange()

        // when
        conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldFail()

        // then
        val initialCountUsers = expectedInitialUsers.size
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(anything(), matching {
                it.size == initialCountUsers
            }).wasInvoked(exactly = once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(anything(), matching {
                it.size == initialCountUsers - 1 // removed 1 failed users with key packages
            }).wasInvoked(exactly = once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(anything(), matching {
                it.size == initialCountUsers - 2  // removed 1 failed user with commit bundle federated error
            }).wasInvoked(exactly = once)

        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
            .with(anything(), matching { it.size == 3 })
            .wasInvoked(exactly = once)
    }

    private class Arrangement :
        MemberDAOArrangement by MemberDAOArrangementImpl() {

        @Mock
        val memberJoinEventHandler = mock(MemberJoinEventHandler::class)

        @Mock
        val memberLeaveEventHandler = mock(MemberLeaveEventHandler::class)

        @Mock
        val conversationMessageTimerEventHandler = mock(ConversationMessageTimerEventHandler::class)

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
        val newGroupConversationSystemMessagesCreator = mock(NewGroupConversationSystemMessagesCreator::class)

        @Mock
        val joinExistingMLSConversation: JoinExistingMLSConversationUseCase = mock(JoinExistingMLSConversationUseCase::class)

        val conversationGroupRepository =
            ConversationGroupRepositoryImpl(
                mlsConversationRepository,
                joinExistingMLSConversation,
                memberJoinEventHandler,
                memberLeaveEventHandler,
                conversationMessageTimerEventHandler,
                conversationDAO,
                conversationApi,
                newConversationMembersRepository,
                lazy { newGroupConversationSystemMessagesCreator },
                TestUser.SELF.id,
                selfTeamIdProvider
            )

        fun withMlsConversationEstablished(additionResult: MLSAdditionResult): Arrangement {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(additionResult))
            return this
        }

        /**
         * Mocks a sequence of [NetworkResponse]s for [ConversationApi.createNewConversation].
         */
        fun withCreateNewConversationAPIResponses(result: Array<NetworkResponse<ConversationResponse>>): Arrangement = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::createNewConversation)
                .whenInvokedWith(anything())
                .thenReturnSequentially(*result)
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
            result: NetworkResponse<ConversationCodeInfo>
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

        /**
         * Mocks sequentially responses from [MLSConversationRepository] with [result].
         */
        fun withAddingMemberToMlsGroupResults(vararg results: Either<CoreFailure, Unit>) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::addMemberToMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturnSequentially(*results)
        }

        fun withSuccessfulRemoveMemberFromMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::removeMembersFromMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulCallToGenerateGuestRoomLinkApi(
            result: EventContentDTO.Conversation.CodeUpdated
        ) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::generateGuestRoomLink)
                .whenInvokedWith(any())
                .thenReturn(
                    NetworkResponse.Success(
                        result,
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

        fun withSuccessfulFetchOfGuestRoomLink(
            result: Flow<ConversationGuestLinkEntity?>
        ) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeGuestRoomLinkByConversationId)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withSuccessfulNewConversationGroupStartedHandled() = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationStarted, fun1<ConversationEntity>())
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulNewConversationMemberHandled() = apply {
            given(newConversationMembersRepository)
                .suspendFunction(newConversationMembersRepository::persistMembersAdditionToTheConversation)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateMessageTimerAPISuccess(event: EventContentDTO.Conversation.MessageTimerUpdate): Arrangement = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateMessageTimer)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        event,
                        emptyMap(),
                        HttpStatusCode.NoContent.value
                    )
                )
        }

        fun withUpdateMessageTimerAPIFailed() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateMessageTimer)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )
        }

        fun withSuccessfulHandleMessageTimerUpdateEvent() = apply {
            given(conversationMessageTimerEventHandler)
                .suspendFunction(conversationMessageTimerEventHandler::handle)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withInsertFailedToAddSystemMessageSuccess(): Arrangement = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationFailedToAddMembers)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withInsertAddedAndFailedSystemMessageSuccess(): Arrangement = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationResolvedMembersAddedAndFailed)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withAddMemberAPIFailsFirstWithUnreachableThenSucceed(networkResponses: Array<NetworkResponse<ConversationMemberAddedResponse>>) =
            apply {
                given(conversationApi)
                    .suspendFunction(conversationApi::addMember)
                    .whenInvokedWith(any(), any())
                    .thenReturnSequentially(*networkResponses)
            }

        fun withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled() = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationStartedUnverifiedWarning)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to conversationGroupRepository
    }

    companion object {
        private const val RAW_GROUP_ID = "mlsGroupId"
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
        val MIXED_PROTOCOL_INFO = ConversationEntity.ProtocolInfo
            .Mixed(
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

        val FEDERATION_ERROR_UNREACHABLE_DOMAINS = NetworkResponse.Error(
            KaliumException.FederationUnreachableException(
                FederationUnreachableResponse(
                    listOf(
                        "unstableDomain1.com",
                        "unstableDomain2.com"
                    )
                )
            )
        )

        val API_SUCCESS_MEMBER_ADDED = NetworkResponse.Success(
            TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
            mapOf(),
            HttpStatusCode.OK.value
        )

        private fun buildCommitBundleFederatedFailure(vararg domains: String = arrayOf("otherDomain")) = Either.Left(
            NetworkFailure.FederatedBackendFailure.FailedDomains(domains.toList())
        )

        val KEY_PACKAGES_NOT_AVAILABLE_FAILURE = Either.Left(CoreFailure.MissingKeyPackages(setOf(TestUser.OTHER_FEDERATED_USER_ID)))
    }
}
