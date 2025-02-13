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
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.legalhold.ListUsersLegalHoldConsent
import com.wire.kalium.logic.data.legalhold.ids
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE
import com.wire.kalium.logic.framework.TestConversation.ADD_SERVICE_TO_CONVERSATION_SUCCESSFUL_RESPONSE
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.thenReturnSequentially
import com.wire.kalium.network.api.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol.MLS
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import com.wire.kalium.network.api.authenticated.conversation.messagetimer.ConversationMessageTimerDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.model.FederationUnreachableResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationGuestLinkEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
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
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            coVerify {
                conversationDAO.insertConversation(any())
            }.wasInvoked(once)

            coVerify {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenSelfUserDoesNotBelongToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            coVerify {
                conversationDAO.insertConversation(any())
            }.wasInvoked(once)

            coVerify {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenSuccess_whenCallingCreateGroupConversation_thenHandleLegalHoldConversationMembersChanged() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        coVerify {
            arrangement.legalHoldHandler.handleConversationMembersChanged(any())
        }.wasInvoked(once)
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
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .withInsertFailedToAddSystemMessageSuccess()
            .arrange()

        val unreachableUserId = TestUser.USER_ID.copy(domain = "unstableDomain2.com")
        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID, unreachableUserId),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            coVerify {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 2 })
            }.wasInvoked(once)

            coVerify {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 1 })
            }.wasInvoked(once)

            coVerify {
                conversationDAO.insertConversation(any())
            }.wasInvoked(once)

            coVerify {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.wasInvoked(once)

            coVerify {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    any(),
                    eq(listOf(unreachableUserId)),
                    eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
                )
            }.wasInvoked(once)
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
            coVerify {
                conversationApi.createNewConversation(any())
            }.wasInvoked(twice)

            coVerify {
                conversationDAO.insertConversation(any())
            }.wasNotInvoked()

            coVerify {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.wasNotInvoked()

            coVerify {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(any(), any(), any())
            }.wasNotInvoked()
        }
    }

    @Test
    fun givenCreatingAGroupConversation_whenThereIsAMissingLegalHoldConsentError_thenRetryIsExecutedWithValidUsersOnly() = runTest {
        val validUsers = listOf(
            TestUser.SELF.id to TestUser.SELF.teamId,
            TestUser.OTHER.id.copy(value = "idWithConsentSameTeam") to TestUser.SELF.teamId,
        )
        val usersWithConsentFromOtherTeams = listOf(TestUser.OTHER.id.copy(value = "idWithConsentOtherTeam") to TestUser.OTHER.teamId)
        val usersWithConsent = validUsers + usersWithConsentFromOtherTeams
        val usersWithoutConsent = listOf(TestUser.OTHER_USER_ID.copy(value = "idWithoutConsent"))
        val usersFailed = listOf(TestUser.OTHER_USER_ID.copy(value = "idFailed"))
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(
                arrayOf(ERROR_MISSING_LEGALHOLD_CONSENT, NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))
            )
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withInsertFailedToAddSystemMessageSuccess()
            .withSuccessfulFetchUsersLegalHoldConsent(ListUsersLegalHoldConsent(usersWithConsent, usersWithoutConsent, usersFailed))
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            usersWithConsent.ids() + usersWithoutConsent + usersFailed,
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            coVerify {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 5 })
            }.wasInvoked(once)

            coVerify {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 2 })
            }.wasInvoked(once)

            coVerify {
                conversationDAO.insertConversation(any())
            }.wasInvoked(once)

            coVerify {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.wasInvoked(once)

            coVerify {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    any(),
                    userIdList = eq(usersWithConsentFromOtherTeams.ids() + usersWithoutConsent + usersFailed),
                    eq(MessageContent.MemberChange.FailedToAdd.Type.LegalHold)
                )
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenCreatingAGroupConversation_whenThereIsAMissingLegalHoldConsentError_thenRetryIsExecutedWithValidUsersOnlyOnce() = runTest {
        val validUsers = listOf(
            TestUser.SELF.id to TestUser.SELF.teamId,
            TestUser.OTHER.id.copy(value = "idWithConsentSameTeam") to TestUser.SELF.teamId,
        )
        val usersWithConsentFromOtherTeams = listOf(TestUser.OTHER.id.copy(value = "idWithConsentOtherTeam") to TestUser.OTHER.teamId)
        val usersWithConsent = validUsers + usersWithConsentFromOtherTeams
        val usersWithoutConsent = listOf(TestUser.OTHER_USER_ID.copy(value = "idWithoutConsent"))
        val usersFailed = listOf(TestUser.OTHER_USER_ID.copy(value = "idFailed"))
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(ERROR_MISSING_LEGALHOLD_CONSENT, ERROR_MISSING_LEGALHOLD_CONSENT))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulFetchUsersLegalHoldConsent(ListUsersLegalHoldConsent(usersWithConsent, usersWithoutConsent, usersFailed))
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            usersWithConsent.ids() + usersWithoutConsent + usersFailed,
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldFail()

        with(arrangement) {
            coVerify {
                conversationApi.createNewConversation(any())
            }.wasInvoked(twice)

            coVerify {
                conversationDAO.insertConversation(any())
            }.wasNotInvoked()

            coVerify {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.wasNotInvoked()

            coVerify {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(any(), any(), any())
            }.wasNotInvoked()
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
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
        )

        result.shouldSucceed()

        with(arrangement) {
            coVerify {
                conversationDAO.insertConversation(any())
            }.wasInvoked(once)

            coVerify {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), eq(true))
            }.wasInvoked(once)

            coVerify {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.wasInvoked(once)
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
                .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
                .withSuccessfulNewConversationGroupStartedHandled()
                .withSuccessfulNewConversationMemberHandled()
                .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
                .withSuccessfulLegalHoldHandleConversationMembersChanged()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            val result = conversationGroupRepository.createGroupConversation(
                GROUP_NAME,
                allWantedMembers.toList(),
                ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
            )

            result.shouldSucceed()

            with(arrangement) {
                coVerify {
                    conversationDAO.insertConversation(any())
                }.wasInvoked(once)

                coVerify {
                    mlsConversationRepository.establishMLSGroup(any(), any(), any(), eq(true))
                }.wasInvoked(once)

                coVerify {
                    newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
                }.wasInvoked(once)

                coVerify {
                    arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                        any(),
                        matches { it.containsAll(missingMembersFromMLSGroup) },
                        eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
                    )
                }.wasInvoked(once)
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

        coVerify {
            arrangement.conversationApi.addMember(any(), eq(TestConversation.ID.toApi()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.conversationApi.addService(eq(addServiceRequest), eq(TestConversation.ID.toApi()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasNotInvoked()
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
                assertIs<UnsupportedOperationException>(it.rootCause)
            }

        coVerify {
            arrangement.conversationApi.addService(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.containsAll(expectedInitialUsers) },
                type = any()
            )
        }.wasInvoked(once)
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
        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                eq(GROUP_ID),
                eq(listOf(TestConversation.USER_1)),
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMixedConversation_whenAddMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MIXED_CONVERSATION)
            .withProtocolInfoById(MIXED_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        coVerify {
            arrangement.conversationApi.addMember(any(), eq(TestConversation.ID.toApi()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                eq(GROUP_ID),
                eq(listOf(TestConversation.USER_1)),
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.conversationApi.removeMember(eq(TestConversation.USER_1.toApi()), eq(TestConversation.ID.toApi()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberLeaveEventHandler.handle(any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.memberLeaveEventHandler.handle(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.memberLeaveEventHandler.handle(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.memberLeaveEventHandler.handle(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(eq(GROUP_ID))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.conversationApi.removeMember(eq(TestConversation.USER_1.toApi()), eq(TestConversation.ID.toApi()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberLeaveEventHandler.handle(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusConversation_whenJoiningConversationSuccessWithChanged_thenResponseIsHandled() = runTest {
        val code = "code"
        val key = "key"
        val uri: String? = null
        val password: String? = null

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

        coVerify {
            arrangement.conversationApi.joinConversation(eq(code), eq(key), eq(uri), eq(password))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusConversation_whenJoiningConversationSuccessWithUnchanged_thenMemberJoinEventHandlerIsNotInvoked() = runTest {
        val code = "code"
        val key = "key"
        val uri: String? = null
        val password: String? = null

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

        coVerify {
            arrangement.conversationApi.joinConversation(eq(code), eq(key), eq(uri), eq(password))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenJoiningConversationSuccessWithChanged_thenAddSelfClientsToMlsGroup() = runTest {
        val code = "code"
        val key = "key"
        val uri: String? = null
        val password: String? = null

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

        coVerify {
            arrangement.conversationApi.joinConversation(eq(code), eq(key), eq(uri), eq(password))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.joinExistingMLSConversation.invoke(
                ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE.event.qualifiedConversation.toModel(),
                null
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                eq(GroupID(MLS_PROTOCOL_INFO.groupId)),
                eq(listOf(TestUser.SELF.id)),
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMixedConversation_whenJoiningConversationSuccessWithChanged_thenAddSelfClientsToMlsGroup() = runTest {
        val code = "code"
        val key = "key"
        val uri: String? = null
        val password: String? = null

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

        coVerify {
            arrangement.conversationApi.joinConversation(eq(code), eq(key), eq(uri), eq(password))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.joinExistingMLSConversation.invoke(
                ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE.event.qualifiedConversation.toModel(),
                null
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                eq(GroupID(MIXED_PROTOCOL_INFO.groupId)),
                eq(listOf(TestUser.SELF.id)),
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.conversationApi.fetchLimitedInformationViaCode(eq(code), eq(key))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.conversationApi.generateGuestRoomLink(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenAFailedApiCall_whenTryingToGenerateANewGuestRoomLink_ThenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFailedCallToGenerateGuestRoomLinkApi()
            .arrange()

        val result = conversationGroupRepository.generateGuestRoomLink(conversationId, null)

        result.shouldFail()

        coVerify {
            arrangement.conversationApi.generateGuestRoomLink(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.conversationApi.generateGuestRoomLink(any(), eq(expectedPassword))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenASuccessApiCall_whenTryingToRevokeGuestRoomLink_ThenCallUpdateGuestLinkInDB() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withSuccessfulCallToRevokeGuestRoomLinkApi()
            .withDeleteGuestLink()
            .arrange()

        val result = conversationGroupRepository.revokeGuestRoomLink(conversationId)

        result.shouldSucceed()

        coVerify {
            arrangement.conversationApi.revokeGuestRoomLink(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.deleteGuestRoomLink(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAFailedApiCall_whenTryingToRevokingGuestRoomLink_ThenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFailedCallToRevokeGuestRoomLinkApi()
            .arrange()

        val result = conversationGroupRepository.revokeGuestRoomLink(conversationId)

        result.shouldFail()

        coVerify {
            arrangement.conversationApi.revokeGuestRoomLink(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.conversationDAO.observeGuestRoomLinkByConversationId(any())
        }.wasInvoked(exactly = once)
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
            Instant.UNIX_FIRST_DATE,
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

        coVerify {
            arrangement.conversationMessageTimerEventHandler.handle(any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.conversationMessageTimerEventHandler.handle(any())
        }.wasNotInvoked()
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
            coVerify {
                arrangement.conversationApi.addMember(matches {
                    it.users.size == expectedFullUserIdsForRequestCount
                }, any())
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.conversationApi.addMember(
                    matches {
                        it.users.size == expectedValidUsersCount && it.users.first().domain != failedDomain
                    }, any()
                )
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.memberJoinEventHandler.handle(any())
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    any(),
                    matches { it.size == expectedValidUsersCount },
                    any()
                )
            }.wasInvoked(exactly = once)
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
            coVerify {
                arrangement.conversationApi.addMember(any(), any())
            }.wasInvoked(exactly = twice)

            coVerify {
                arrangement.memberJoinEventHandler.handle(any())
            }.wasNotInvoked()

            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = matches {
                        it.containsAll(expectedInitialUsers)
                    },
                    type = any()
                )
            }.wasInvoked(once)
        }

    @Test
    fun givenAConversationFailsWithUnreachableAndNotFromUsersInRequest_whenAddingMembers_thenRetryIsNotExecutedAndCreateSysMessage() =
        runTest {
            val conversation = TestConversation.CONVERSATION.copy(id = ConversationId("valueConvo", "domainConvo"))
            // given
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withConversationDetailsById(conversation)
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
            coVerify {
                arrangement.conversationApi.addMember(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.memberJoinEventHandler.handle(any())
            }.wasNotInvoked()

            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = conversation.id,
                    userIdList = expectedInitialUsersNotFromUnreachableInformed,
                    type = MessageContent.MemberChange.FailedToAdd.Type.Federation
                )
            }.wasInvoked(once)
        }

    @Test
    fun givenAConversationFailsWithGeneralFederationError_whenAddingMembers_thenRetryIsNotExecutedAndCreateSysMessage() =
        runTest {
            // given
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withConversationDetailsById(TestConversation.CONVERSATION)
                .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
                .withFetchUsersIfUnknownByIdsSuccessful()
                .withAddMemberAPIFailsFirstWithUnreachableThenSucceed(arrayOf(FEDERATION_ERROR_GENERAL, FEDERATION_ERROR_GENERAL))
                .withSuccessfulHandleMemberJoinEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsersNotFromUnreachableInformed = listOf(TestConversation.USER_1)
            conversationGroupRepository.addMembers(expectedInitialUsersNotFromUnreachableInformed, TestConversation.ID).shouldFail()

            // then
            coVerify {
                arrangement.conversationApi.addMember(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.memberJoinEventHandler.handle(any())
            }.wasNotInvoked()

            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = matches {
                        it.containsAll(expectedInitialUsersNotFromUnreachableInformed)
                    },
                    type = eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
                )
            }.wasInvoked(once)
        }

    @Test
    fun givenAValidConversation_whenCreating_thenConversationIsCreatedAndUnverifiedWarningSystemMessagePersisted() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            coVerify {
                conversationDAO.insertConversation(any())
            }.wasInvoked(once)

            coVerify {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
            }.wasInvoked(once)
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
        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(), matches {
                    it.size == expectedFullUserIdsForRequestCount
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(), matches {
                    it.size == expectedValidUsersWithKeyPackagesCount && it.first() == TestConversation.USER_1
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.size == 1 },
                type = any()
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(), matches {
                    it.size == expectedFullUserIdsForRequestCount
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(), matches {
                    it.size == expectedValidUsersWithKeyPackagesCount && it.first() == TestConversation.USER_1
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.size == 1 },
                type = any()
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(), matches {
                    it.size == initialCountUsers
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(), matches {
                    it.size == initialCountUsers - 1 // removed 1 failed users with key packages
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(), matches {
                    it.size == initialCountUsers - 2  // removed 1 failed user with commit bundle federated error
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.size == 3 },
                type = any()
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPIFailsWithMissingLHConsent_whenAddingMembersToConversation_thenShouldRetryWithValidUsers() =
        runTest {
            // given
            val validUsers = listOf(
                TestUser.SELF.id to TestUser.SELF.teamId,
                TestUser.OTHER.id.copy(value = "idWithConsentSameTeam") to TestUser.SELF.teamId,
            )
            val usersWithConsentFromOtherTeams = listOf(TestUser.OTHER.id.copy(value = "idWithConsentOtherTeam") to TestUser.OTHER.teamId)
            val usersWithConsent = validUsers + usersWithConsentFromOtherTeams
            val usersWithoutConsent = listOf(TestUser.OTHER_USER_ID.copy(value = "idWithoutConsent"))
            val usersFailed = listOf(TestUser.OTHER_USER_ID.copy(value = "idFailed"))
            val expectedInitialUsers = usersWithConsent.ids() + usersWithoutConsent + usersFailed
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
                .withConversationDetailsById(TestConversation.CONVERSATION)
                .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
                .withFetchUsersIfUnknownByIdsSuccessful()
                .withAddMemberAPIFailsFirstWithUnreachableThenSucceed(arrayOf(ERROR_MISSING_LEGALHOLD_CONSENT, API_SUCCESS_MEMBER_ADDED))
                .withSuccessfulHandleMemberJoinEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .withSuccessfulFetchUsersLegalHoldConsent(ListUsersLegalHoldConsent(usersWithConsent, usersWithoutConsent, usersFailed))
                .arrange()
            // when
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldSucceed()
            // then
            coVerify {
                arrangement.conversationApi.addMember(
                    addParticipantRequest = matches { it.users == expectedInitialUsers.map { it.toApi() } },
                    conversationId = any()
                )
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.conversationApi.addMember(matches { it.users == validUsers.ids().map { it.toApi() } }, any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.memberJoinEventHandler.handle(any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = eq(usersWithConsentFromOtherTeams.ids() + usersWithoutConsent + usersFailed),
                    type = any()
                )
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenAConversationAndAPIFailsWithMissingLHConsent_whenAddingMembersToConversation_thenRetryIsExecutedWithValidUsersOnlyOnce() =
        runTest {
            // given
            val validUsers = listOf(
                TestUser.SELF.id to TestUser.SELF.teamId,
                TestUser.OTHER.id.copy(value = "idWithConsentSameTeam") to TestUser.SELF.teamId,
            )
            val usersWithConsentFromOtherTeams = listOf(TestUser.OTHER.id.copy(value = "idWithConsentOtherTeam") to TestUser.OTHER.teamId)
            val usersWithConsent = validUsers + usersWithConsentFromOtherTeams
            val usersWithoutConsent = listOf(TestUser.OTHER_USER_ID.copy(value = "idWithoutConsent"))
            val usersFailed = listOf(TestUser.OTHER_USER_ID.copy(value = "idFailed"))
            val expectedInitialUsers = usersWithConsent.ids() + usersWithoutConsent + usersFailed
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
                .withConversationDetailsById(TestConversation.CONVERSATION)
                .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
                .withFetchUsersIfUnknownByIdsSuccessful()
                .withAddMemberAPIFailsFirstWithUnreachableThenSucceed(
                    arrayOf(ERROR_MISSING_LEGALHOLD_CONSENT, ERROR_MISSING_LEGALHOLD_CONSENT)
                )
                .withSuccessfulHandleMemberJoinEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .withSuccessfulFetchUsersLegalHoldConsent(ListUsersLegalHoldConsent(usersWithConsent, usersWithoutConsent, usersFailed))
                .arrange()
            // when
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldFail()
            // then
            coVerify {
                arrangement.conversationApi.addMember(any(), any())
            }.wasInvoked(exactly = twice)
            coVerify {
                arrangement.memberJoinEventHandler.handle(any())
            }.wasNotInvoked()
            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = eq(expectedInitialUsers),
                    type = any()
                )
            }.wasInvoked(once)
        }

    @Test
    fun givenAFetchingConversationCodeSuccess_whenSyncingCode_thenUpdateLocally() = runTest {
        val expected = NetworkResponse.Success(
            ConversationInviteLinkResponse(
                uri = "uri",
                code = "code",
                key = "key",
                hasPassword = false
            ),
            emptyMap(),
            200
        )

        val conversationId = ConversationId("value", "domain")

        val accountUrl = "accountUrl.com"

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withRemoteFetchCode(expected)
            .withInsertConversationSuccess()
            .arrange()

        conversationGroupRepository.updateGuestRoomLink(conversationId, accountUrl)

        coVerify {
            arrangement.conversationApi.guestLinkInfo(eq(conversationId.toApi()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.updateGuestRoomLink(eq(conversationId.toDao()), eq("uri"), eq(expected.value.hasPassword))
        }.wasInvoked(exactly = once)
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

        @Mock
        val legalHoldHandler: LegalHoldHandler = mock(LegalHoldHandler::class)

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
                userRepository,
                lazy { newGroupConversationSystemMessagesCreator },
                TestUser.SELF.id,
                selfTeamIdProvider,
                legalHoldHandler
            )

        suspend fun withMlsConversationEstablished(additionResult: MLSAdditionResult): Arrangement = apply {
            coEvery {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any())
            }.returns(Either.Right(additionResult))
        }

        /**
         * Mocks a sequence of [NetworkResponse]s for [ConversationApi.createNewConversation].
         */
        suspend fun withCreateNewConversationAPIResponses(result: Array<NetworkResponse<ConversationResponse>>): Arrangement = apply {
            coEvery { conversationApi.createNewConversation(any()) }
                .thenReturnSequentially(*result)
        }

        suspend fun withSelfTeamId(result: Either<StorageFailure, TeamId?>): Arrangement = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(result)
        }

        suspend fun withInsertConversationSuccess(): Arrangement = apply {
            coEvery {
                conversationDAO.insertConversation(any())
            }.returns(Unit)
        }

        suspend fun withFetchLimitedConversationInfo(
            code: String,
            key: String,
            result: NetworkResponse<ConversationCodeInfo>
        ): Arrangement = apply {
            coEvery {
                conversationApi.fetchLimitedInformationViaCode(eq(code), eq(key))
            }.returns(result)
        }

        suspend fun withJoinConversationAPIResponse(
            code: String,
            key: String,
            uri: String?,
            result: NetworkResponse<ConversationMemberAddedResponse>
        ): Arrangement = apply {
            coEvery {
                conversationApi.joinConversation(eq(code), eq(key), eq(uri), any())
            }.returns(result)
        }

        suspend fun withJoinExistingMlsConversationSucceeds() = apply {
            coEvery {
                joinExistingMLSConversation.invoke(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withConversationDetailsById(conversation: Conversation) = apply {
            coEvery {
                conversationRepository.getConversationById(any())
            }.returns(Either.Right(conversation))
        }

        suspend fun withConversationDetailsById(result: ConversationViewEntity?) = apply {
            coEvery {
                conversationDAO.getConversationDetailsById(any())
            }.returns(result)
        }

        suspend fun withConversationById(result: ConversationEntity?) = apply {
            coEvery {
                conversationDAO.getConversationById(any())
            }.returns(result)
        }

        suspend fun withProtocolInfoById(result: ConversationEntity.ProtocolInfo) = apply {
            coEvery {
                conversationDAO.getConversationProtocolInfo(any())
            }.returns(result)
        }

        suspend fun withAddMemberAPISucceedChanged() = apply {
            coEvery {
                conversationApi.addMember(any(), any())
            }.returns(
                NetworkResponse.Success(
                    ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withAddServiceAPISucceedChanged() = apply {
            coEvery {
                conversationApi.addService(any(), any())
            }.returns(
                NetworkResponse.Success(
                    ADD_SERVICE_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withAddMemberAPISucceedUnchanged() = apply {
            coEvery {
                conversationApi.addMember(any(), any())
            }.returns(
                NetworkResponse.Success(
                    ConversationMemberAddedResponse.Unchanged,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withAddMemberAPIFailed() = apply {
            coEvery {
                conversationApi.addMember(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withDeleteMemberAPISucceedChanged() = apply {
            coEvery {
                conversationApi.removeMember(any(), any())
            }.returns(
                NetworkResponse.Success(
                    TestConversation.REMOVE_MEMBER_FROM_CONVERSATION_SUCCESSFUL_RESPONSE,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withDeleteMemberAPISucceedUnchanged() = apply {
            coEvery {
                conversationApi.removeMember(any(), any())
            }.returns(
                NetworkResponse.Success(
                    ConversationMemberRemovedResponse.Unchanged,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withDeleteMemberAPIFailed() = apply {
            coEvery {
                conversationApi.removeMember(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withFetchUsersIfUnknownByIdsSuccessful() = apply {
            coEvery {
                userRepository.fetchUsersIfUnknownByIds(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulHandleMemberJoinEvent() = apply {
            coEvery {
                memberJoinEventHandler.handle(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulHandleMemberLeaveEvent() = apply {
            coEvery {
                memberLeaveEventHandler.handle(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulLeaveMLSGroup() = apply {
            coEvery {
                mlsConversationRepository.leaveGroup(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulAddMemberToMLSGroup() = apply {
            coEvery {
                mlsConversationRepository.addMemberToMLSGroup(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        /**
         * Mocks sequentially responses from [MLSConversationRepository] with [result].
         */
        suspend fun withAddingMemberToMlsGroupResults(vararg results: Either<CoreFailure, Unit>) = apply {
            coEvery {
                mlsConversationRepository.addMemberToMLSGroup(any(), any(), any())
            }.thenReturnSequentially(*results)
        }

        suspend fun withSuccessfulRemoveMemberFromMLSGroup() = apply {
            coEvery {
                mlsConversationRepository.removeMembersFromMLSGroup(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulCallToGenerateGuestRoomLinkApi(
            result: EventContentDTO.Conversation.CodeUpdated
        ) = apply {
            coEvery {
                conversationApi.generateGuestRoomLink(any(), any())
            }.returns(
                NetworkResponse.Success(
                    result,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withFailedCallToGenerateGuestRoomLinkApi() = apply {
            coEvery {
                conversationApi.generateGuestRoomLink(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withDeleteGuestLink() = apply {
            coEvery {
                conversationDAO.deleteGuestRoomLink(any())
            }.returns(Unit)
        }

        suspend fun withSuccessfulCallToRevokeGuestRoomLinkApi() = apply {
            coEvery {
                conversationApi.revokeGuestRoomLink(any())
            }.returns(
                NetworkResponse.Success(
                    Unit,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withFailedCallToRevokeGuestRoomLinkApi() = apply {
            coEvery {
                conversationApi.revokeGuestRoomLink(any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withSuccessfulFetchOfGuestRoomLink(
            result: Flow<ConversationGuestLinkEntity?>
        ) = apply {
            coEvery {
                conversationDAO.observeGuestRoomLinkByConversationId(any())
            }.returns(result)
        }

        suspend fun withSuccessfulNewConversationGroupStartedHandled() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationStarted(any(), any(), any())
            }.returns(Either.Right(Unit))
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationStarted(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulNewConversationMemberHandled() = apply {
            coEvery {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateMessageTimerAPISuccess(event: EventContentDTO.Conversation.MessageTimerUpdate): Arrangement = apply {
            coEvery {
                conversationApi.updateMessageTimer(any(), any())
            }.returns(
                NetworkResponse.Success(
                    event,
                    emptyMap(),
                    HttpStatusCode.NoContent.value
                )
            )
        }

        suspend fun withUpdateMessageTimerAPIFailed() = apply {
            coEvery {
                conversationApi.updateMessageTimer(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withSuccessfulHandleMessageTimerUpdateEvent() = apply {
            coEvery {
                conversationMessageTimerEventHandler.handle(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withInsertFailedToAddSystemMessageSuccess(): Arrangement = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withAddMemberAPIFailsFirstWithUnreachableThenSucceed(networkResponses: Array<NetworkResponse<ConversationMemberAddedResponse>>) =
            apply {
                coEvery { conversationApi.addMember(any(), any()) }
                    .thenReturnSequentially(*networkResponses)
            }

        suspend fun withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withRemoteFetchCode(
            result: NetworkResponse<ConversationInviteLinkResponse>
        ) = apply {
            coEvery {
                conversationApi.guestLinkInfo(any())
            }.returns(result)
        }

        suspend fun withSuccessfulFetchUsersLegalHoldConsent(result: ListUsersLegalHoldConsent) = apply {
            coEvery {
                userRepository.fetchUsersLegalHoldConsent(any())
            }.returns(Either.Right(result))
        }

        suspend fun withSuccessfulLegalHoldHandleConversationMembersChanged() = apply {
            coEvery {
                legalHoldHandler.handleConversationMembersChanged(any())
            }.returns(Either.Right(Unit))
        }


        fun arrange() = this to conversationGroupRepository
    }

    private companion object {
        val CIPHER_SUITE = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        private const val RAW_GROUP_ID = "mlsGroupId"
        val GROUP_ID = GroupID(RAW_GROUP_ID)
        val PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus
        val MLS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo
            .MLS(
                RAW_GROUP_ID,
                groupState = ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = CIPHER_SUITE
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

        val FEDERATION_ERROR_GENERAL = NetworkResponse.Error(
            KaliumException.FederationError(ErrorResponse(422, "", "federation-remote-error"))
        )

        val ERROR_MISSING_LEGALHOLD_CONSENT = NetworkResponse.Error(
            KaliumException.InvalidRequestError(
                ErrorResponse(
                    code = HttpStatusCode.Forbidden.value,
                    message = "",
                    label = "missing-legalhold-consent"
                )
            )
        )

        val API_SUCCESS_MEMBER_ADDED = NetworkResponse.Success(
            ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
            mapOf(),
            HttpStatusCode.OK.value
        )

        private fun buildCommitBundleFederatedFailure(vararg domains: String = arrayOf("otherDomain")) = Either.Left(
            NetworkFailure.FederatedBackendFailure.FailedDomains(domains.toList())
        )

        val KEY_PACKAGES_NOT_AVAILABLE_FAILURE = Either.Left(CoreFailure.MissingKeyPackages(setOf(TestUser.OTHER_FEDERATED_USER_ID)))
    }
}
