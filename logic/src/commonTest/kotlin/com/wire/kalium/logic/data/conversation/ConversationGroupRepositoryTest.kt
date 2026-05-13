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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
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
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
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
import com.wire.kalium.network.api.model.FederationErrorResponse
import com.wire.kalium.network.exceptions.FederationError
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationGuestLinkEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentiallyReturns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldHandler.handleConversationMembersChanged(any())
        }
    }

    @Test
    fun givenSuccess_whenCallingCreateGroupConversation_AndsAppsEnabledPersistSystemMessage() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationAppsAccessIfEnabled(
                any(),
                any(),
                any(),
                any(),
                any<ConversationEntity.Type>()
            )
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
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .withInsertFailedToAddSystemMessageSuccess()
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val unreachableUserId = TestUser.USER_ID.copy(domain = "unstableDomain2.com")
        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID, unreachableUserId),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 2 })
            }

            verifySuspend(VerifyMode.exactly(1)) {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 1 })
            }

            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    any(),
                    eq(listOf(unreachableUserId)),
                    eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
                )
            }
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
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldFail()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(2)) {
                conversationApi.createNewConversation(any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(any(), any(), any())
            }
        }
    }

    @Test
    fun givenCreatingAGroupConversation_whenThereIsAConflictingBackendsError_thenDoNotExecuteAutomaticRetry() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(FEDERATION_ERROR_CONFLICTING_BACKENDS, FEDERATION_ERROR_CONFLICTING_BACKENDS))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .arrange()

        val conflictingUserId = TestUser.USER_ID.copy(domain = "conflictingDomain2.com")
        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID, conflictingUserId),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldFail()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationApi.createNewConversation(any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(any(), any(), any())
            }
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            usersWithConsent.ids() + usersWithoutConsent + usersFailed,
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 5 })
            }

            verifySuspend(VerifyMode.exactly(1)) {
                conversationApi.createNewConversation(matches { it.qualifiedUsers?.size == 2 })
            }

            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    any(),
                    userIdList = eq(usersWithConsentFromOtherTeams.ids() + usersWithoutConsent + usersFailed),
                    eq(MessageContent.MemberChange.FailedToAdd.Type.LegalHold)
                )
            }
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
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldFail()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(2)) {
                conversationApi.createNewConversation(any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(any(), any(), any())
            }
        }
    }

    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(conversationResponse, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withMlsConversationEstablished(MLSAdditionResult(setOf(TestUser.USER_ID), emptySet(), emptySet()))
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), eq(true))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }
        }
    }

    @Test
    fun givenMLSProtocolIsUsedAndSomeUsersHaveNoKeyPackages_whenCallingCreateGroupConversation_thenMissingKeyPackagesSystemMessageIsPersisted() =
        runTest {
            val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)
            val usersWithNoKeyPackages = setOf(TestUser.OTHER_USER_ID, TestUser.OTHER_USER_ID_2)
            val successfullyAddedUsers = setOf(TestUser.USER_ID)
            val allWantedMembers = successfullyAddedUsers + usersWithNoKeyPackages
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(conversationResponse, emptyMap(), 201)))
                .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
                .withInsertConversationSuccess()
                .withMlsConversationEstablished(
                    MLSAdditionResult(
                        successfullyAddedUsers = setOf(TestUser.USER_ID),
                        usersWithoutKeyPackages = usersWithNoKeyPackages,
                        usersWithUnreachableBackend = emptySet()
                    )
                )
                .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
                .withSuccessfulNewConversationGroupStartedHandled()
                .withSuccessfulNewConversationMemberHandled()
                .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
                .withSuccessfulLegalHoldHandleConversationMembersChanged()
                .withInsertFailedToAddSystemMessageSuccess()
                .withConversationAppsAccessIfEnabled()
                .arrange()

            val result = conversationGroupRepository.createGroupConversation(
                GROUP_NAME,
                allWantedMembers.toList(),
                CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS)
            )

            result.shouldSucceed()

            with(arrangement) {
                verifySuspend(VerifyMode.exactly(1)) {
                    arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                        any(),
                        matches { it.containsAll(usersWithNoKeyPackages) },
                        eq(MessageContent.MemberChange.FailedToAdd.Type.MissingKeyPackages)
                    )
                }

                verifySuspend(VerifyMode.exactly(0)) {
                    arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                        any(),
                        any(),
                        eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
                    )
                }
            }
        }

    @Test
    fun givenMLSProtocolIsUsedAndSomeUsersAreOnUnreachableBackend_whenCallingCreateGroupConversation_thenFederationSystemMessageIsPersisted() =
        runTest {
            val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)
            val usersOnUnreachableBackend = setOf(
                TestUser.OTHER_USER_ID.copy(domain = "unreachable.com"),
                TestUser.OTHER_USER_ID_2.copy(domain = "unreachable.com")
            )
            val successfullyAddedUsers = setOf(TestUser.USER_ID)
            val allWantedMembers = successfullyAddedUsers + usersOnUnreachableBackend
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(conversationResponse, emptyMap(), 201)))
                .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
                .withInsertConversationSuccess()
                .withMlsConversationEstablished(
                    MLSAdditionResult(
                        successfullyAddedUsers = setOf(TestUser.USER_ID),
                        usersWithoutKeyPackages = emptySet(),
                        usersWithUnreachableBackend = usersOnUnreachableBackend
                    )
                )
                .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
                .withSuccessfulNewConversationGroupStartedHandled()
                .withSuccessfulNewConversationMemberHandled()
                .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
                .withSuccessfulLegalHoldHandleConversationMembersChanged()
                .withInsertFailedToAddSystemMessageSuccess()
                .withConversationAppsAccessIfEnabled()
                .arrange()

            val result = conversationGroupRepository.createGroupConversation(
                GROUP_NAME,
                allWantedMembers.toList(),
                CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS)
            )

            result.shouldSucceed()

            with(arrangement) {
                verifySuspend(VerifyMode.exactly(1)) {
                    arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                        any(),
                        matches { it.containsAll(usersOnUnreachableBackend) },
                        eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
                    )
                }

                verifySuspend(VerifyMode.exactly(0)) {
                    arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                        any(),
                        any(),
                        eq(MessageContent.MemberChange.FailedToAdd.Type.MissingKeyPackages)
                    )
                }
            }
        }

    @Test
    fun givenProteusConversation_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedChanged()
            .withEmitLocalEvent()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.addMember(any(), eq(TestConversation.ID.toApi()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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
            .withEmitLocalEvent()
            .arrange()

        conversationGroupRepository.addService(serviceID, TestConversation.ID)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.addService(eq(addServiceRequest), eq(TestConversation.ID.toApi()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationApi.addService(any(), any())
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.containsAll(expectedInitialUsers) },
                type = any()
            )
        }
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
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                eq(GROUP_ID),
                eq(listOf(TestConversation.USER_1)),
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }
    }

    @Test
    fun givenMixedConversation_whenAddMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MIXED_CONVERSATION)
            .withProtocolInfoById(MIXED_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .withEmitLocalEvent()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.addMember(any(), eq(TestConversation.ID.toApi()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                eq(GROUP_ID),
                eq(listOf(TestConversation.USER_1)),
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }
    }

    @Test
    fun givenProteusConversation_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withEmitLocalEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.removeMember(eq(TestConversation.USER_1.toApi()), eq(TestConversation.ID.toApi()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
    }

    @Test
    fun givenMLSConversation_whenRemovingLeavingConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulLeaveMLSGroup()
            .withEmitLocalEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestUser.SELF.id, TestConversation.ID)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), eq(GROUP_ID))
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(any(), eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }
    }

    @Test
    fun givenMixedConversation_whenRemoveMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MIXED_CONVERSATION)
            .withProtocolInfoById(MIXED_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulRemoveMemberFromMLSGroup()
            .withEmitLocalEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.removeMember(eq(TestConversation.USER_1.toApi()), eq(TestConversation.ID.toApi()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.localEventRepository.emitLocalEvent(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(any(), eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
        }
    }

    @Test
    fun givenJoiningConversationSuccessWithChanged_thenApiIsCalled() = runTest {
        val code = "code"
        val key = "key"
        val uri: String? = null
        val password: String? = null

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withJoinConversationAPIResponse(
                code,
                key,
                uri,
                NetworkResponse.Success(ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE, emptyMap(), 200)
            )
            .arrange()

        conversationGroupRepository.joinViaInviteCode(code, key, uri, password)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.joinConversation(eq(code), eq(key), eq(uri), eq(password))
        }
    }

    @Test
    fun givenJoiningConversationSuccessWithUnchanged_thenApiIsCalled() = runTest {
        val code = "code"
        val key = "key"
        val uri: String? = null
        val password: String? = null

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withJoinConversationAPIResponse(
                code,
                key,
                uri,
                NetworkResponse.Success(ConversationMemberAddedResponse.Unchanged, emptyMap(), 204)
            )
            .arrange()

        conversationGroupRepository.joinViaInviteCode(code, key, uri, password)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.joinConversation(eq(code), eq(key), eq(uri), eq(password))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.fetchLimitedInformationViaCode(eq(code), eq(key))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.generateGuestRoomLink(any(), any())
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }
    }

    @Test
    fun givenAFailedApiCall_whenTryingToGenerateANewGuestRoomLink_ThenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFailedCallToGenerateGuestRoomLinkApi()
            .arrange()

        val result = conversationGroupRepository.generateGuestRoomLink(conversationId, null)

        result.shouldFail()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.generateGuestRoomLink(any(), any())
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.generateGuestRoomLink(any(), eq(expectedPassword))
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.revokeGuestRoomLink(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.deleteGuestRoomLink(any())
        }
    }

    @Test
    fun givenAFailedApiCall_whenTryingToRevokingGuestRoomLink_ThenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withFailedCallToRevokeGuestRoomLinkApi()
            .arrange()

        val result = conversationGroupRepository.revokeGuestRoomLink(conversationId)

        result.shouldFail()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.revokeGuestRoomLink(any())
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationDAO.updateGuestRoomLink(any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.observeGuestRoomLinkByConversationId(any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationMessageTimerEventHandler.handle(any())
        }
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationMessageTimerEventHandler.handle(any())
        }
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
                .withEmitLocalEvent()
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.addMember(matches {
                    it.users.size == expectedFullUserIdsForRequestCount
                }, any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.addMember(
                    matches {
                        it.users.size == expectedValidUsersCount && it.users.first().domain != failedDomain
                    }, any()
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.localEventRepository.emitLocalEvent(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    any(),
                    matches { it.size == expectedValidUsersCount },
                    any()
                )
            }
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
                .withEmitLocalEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsers = listOf(TestConversation.USER_1.copy(domain = failedDomain), TestUser.OTHER_FEDERATED_USER_ID)
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldFail()

            // then
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.conversationApi.addMember(any(), any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.localEventRepository.emitLocalEvent(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = matches {
                        it.containsAll(expectedInitialUsers)
                    },
                    type = any()
                )
            }
        }

    @Test
    fun givenAConversationFailsWithUnreachableAndNoValidExtractedUsers_whenAddingMembers_thenRetryIsNotExecutedAndCreateSysMessage() =
        runTest {
            val failedDomain = "unstableDomain1.com"
            val conversation = TestConversation.CONVERSATION.copy(id = ConversationId("valueConvo", "domainConvo"))
            // given
            val (arrangement, conversationGroupRepository) = Arrangement()
                .withConversationDetailsById(conversation)
                .withProtocolInfoById(PROTEUS_PROTOCOL_INFO)
                .withFetchUsersIfUnknownByIdsSuccessful()
                .withAddMemberAPIFailsFirstWithUnreachableThenSucceed(
                    arrayOf(FEDERATION_ERROR_UNREACHABLE_DOMAINS, FEDERATION_ERROR_UNREACHABLE_DOMAINS)
                )
                .withEmitLocalEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsers = listOf(
                TestConversation.USER_1.copy(domain = failedDomain) // only failed domain user so after extracting there are no valid ones
            )
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldFail()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.addMember(any(), any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.localEventRepository.emitLocalEvent(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = conversation.id,
                    userIdList = expectedInitialUsers,
                    type = MessageContent.MemberChange.FailedToAdd.Type.Federation
                )
            }
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
                .withEmitLocalEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsersNotFromUnreachableInformed = listOf(TestConversation.USER_1)
            conversationGroupRepository.addMembers(expectedInitialUsersNotFromUnreachableInformed, TestConversation.ID).shouldFail()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.addMember(any(), any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.localEventRepository.emitLocalEvent(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = conversation.id,
                    userIdList = expectedInitialUsersNotFromUnreachableInformed,
                    type = MessageContent.MemberChange.FailedToAdd.Type.Federation
                )
            }
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
                .withEmitLocalEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .arrange()

            // when
            val expectedInitialUsersNotFromUnreachableInformed = listOf(TestConversation.USER_1)
            conversationGroupRepository.addMembers(expectedInitialUsersNotFromUnreachableInformed, TestConversation.ID).shouldFail()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.addMember(any(), any())
            }

            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.localEventRepository.emitLocalEvent(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = matches {
                        it.containsAll(expectedInitialUsersNotFromUnreachableInformed)
                    },
                    type = eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
                )
            }
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
            }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                any(),
                matches {
                    it.size == expectedFullUserIdsForRequestCount
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                any(),
                matches {
                    it.size == expectedValidUsersWithKeyPackagesCount && it.first() == TestConversation.USER_1
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.size == 1 },
                type = any()
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                any(),
                matches {
                    it.size == expectedFullUserIdsForRequestCount
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                any(),
                matches {
                    it.size == expectedValidUsersWithKeyPackagesCount && it.first() == TestConversation.USER_1
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.size == 1 },
                type = any()
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                any(),
                matches {
                    it.size == initialCountUsers
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                any(),
                matches {
                    it.size == initialCountUsers - 1 // removed 1 failed users with key packages
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                any(),
                any(),
                matches {
                    it.size == initialCountUsers - 2  // removed 1 failed user with commit bundle federated error
                },
                eq(CipherSuite.fromTag(CIPHER_SUITE.cipherSuiteTag))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = matches { it.size == 3 },
                type = any()
            )
        }
    }

    @Test
    fun givenNoNetworkConnection_whenAddingMembers_thenUnknownSystemMessageIsPersistedAndOperationFails() = runTest {
        // given
        val expectedInitialUsers = listOf(TestConversation.USER_1, TestUser.OTHER_FEDERATED_USER_ID)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withProtocolInfoById(MLS_PROTOCOL_INFO)
            .withAddingMemberToMlsGroupResults(
                Either.Left(NetworkFailure.NoNetworkConnection(null))
            )
            .withInsertFailedToAddSystemMessageSuccess()
            .arrange()

        // when
        conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldFail()

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = any(),
                type = eq(MessageContent.MemberChange.FailedToAdd.Type.Unknown)
            )
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                conversationId = any(),
                userIdList = any(),
                type = eq(MessageContent.MemberChange.FailedToAdd.Type.Federation)
            )
        }
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
                .withEmitLocalEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .withSuccessfulFetchUsersLegalHoldConsent(ListUsersLegalHoldConsent(usersWithConsent, usersWithoutConsent, usersFailed))
                .arrange()
            // when
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldSucceed()
            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.addMember(
                    addParticipantRequest = matches { it.users == expectedInitialUsers.map { it.toApi() } },
                    conversationId = any()
                )
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.addMember(matches { it.users == validUsers.ids().map { it.toApi() } }, any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.localEventRepository.emitLocalEvent(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = eq(usersWithConsentFromOtherTeams.ids() + usersWithoutConsent + usersFailed),
                    type = any()
                )
            }
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
                .withEmitLocalEvent()
                .withInsertFailedToAddSystemMessageSuccess()
                .withSuccessfulFetchUsersLegalHoldConsent(ListUsersLegalHoldConsent(usersWithConsent, usersWithoutConsent, usersFailed))
                .arrange()
            // when
            conversationGroupRepository.addMembers(expectedInitialUsers, TestConversation.ID).shouldFail()
            // then
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.conversationApi.addMember(any(), any())
            }
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.localEventRepository.emitLocalEvent(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(
                    conversationId = any(),
                    userIdList = eq(expectedInitialUsers),
                    type = any()
                )
            }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.guestLinkInfo(eq(conversationId.toApi()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.updateGuestRoomLink(eq(conversationId.toDao()), eq("uri"), eq(expected.value.hasPassword))
        }
    }

    @Test
    fun givenAValidConversation_whenCreating_thenConversationIsCreatedAndCellsFeatureSystemMessagePersisted() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPIResponses(arrayOf(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201)))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationById(TestConversation.ENTITY_GROUP.copy(protocolInfo = PROTEUS_PROTOCOL_INFO))
            .withSuccessfulNewConversationGroupStartedHandled()
            .withSuccessfulNewConversationMemberHandled()
            .withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled()
            .withSuccessfulLegalHoldHandleConversationMembersChanged()
            .withConversationAppsAccessIfEnabled()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.insertConversation(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                newGroupConversationSystemMessagesCreator.conversationCellStatus(any())
            }
        }
    }

    private class Arrangement :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler = mock(mode = MockMode.autoUnit)
        val userRepository: UserRepository = mock(mode = MockMode.autoUnit)
        val conversationRepository: ConversationRepository = mock(mode = MockMode.autoUnit)
        val mlsConversationRepository: MLSConversationRepository = mock(mode = MockMode.autoUnit)
        val conversationDAO: ConversationDAO = mock(mode = MockMode.autoUnit)
        val conversationApi: ConversationApi = mock(mode = MockMode.autoUnit)
        val selfTeamIdProvider: SelfTeamIdProvider = mock(mode = MockMode.autoUnit)
        val newConversationMembersRepository: NewConversationMembersRepository = mock(mode = MockMode.autoUnit)
        val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator = mock(mode = MockMode.autoUnit)
        val legalHoldHandler: LegalHoldHandler = mock(mode = MockMode.autoUnit)
        val localEventRepository: LocalEventRepository = mock(mode = MockMode.autoUnit)

        val conversationGroupRepository =
            ConversationGroupRepositoryImpl(
                mlsConversationRepository,
                localEventRepository,
                conversationMessageTimerEventHandler,
                conversationDAO,
                conversationApi,
                newConversationMembersRepository,
                userRepository,
                lazy { newGroupConversationSystemMessagesCreator },
                TestUser.SELF.id,
                selfTeamIdProvider,
                legalHoldHandler,
                cryptoTransactionProvider
            )

        suspend fun withMlsConversationEstablished(additionResult: MLSAdditionResult): Arrangement = apply {
            everySuspend {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            }.returns(Either.Right(additionResult))
        }

        /**
         * Mocks a sequence of [NetworkResponse]s for [ConversationApi.createNewConversation].
         */
        suspend fun withCreateNewConversationAPIResponses(result: Array<NetworkResponse<ConversationResponse>>): Arrangement = apply {
            everySuspend { conversationApi.createNewConversation(any()) }
                .sequentiallyReturns(result.asList())
        }

        suspend fun withSelfTeamId(result: Either<StorageFailure, TeamId?>): Arrangement = apply {
            everySuspend {
                selfTeamIdProvider.invoke()
            }.returns(result)
        }

        suspend fun withInsertConversationSuccess(): Arrangement = apply {
            everySuspend {
                conversationDAO.insertConversation(any())
            }.returns(Unit)
        }

        suspend fun withFetchLimitedConversationInfo(
            code: String,
            key: String,
            result: NetworkResponse<ConversationCodeInfo>
        ): Arrangement = apply {
            everySuspend {
                conversationApi.fetchLimitedInformationViaCode(eq(code), eq(key))
            }.returns(result)
        }

        suspend fun withJoinConversationAPIResponse(
            code: String,
            key: String,
            uri: String?,
            result: NetworkResponse<ConversationMemberAddedResponse>
        ): Arrangement = apply {
            everySuspend {
                conversationApi.joinConversation(eq(code), eq(key), eq(uri), any())
            }.returns(result)
        }

        suspend fun withConversationDetailsById(conversation: Conversation) = apply {
            everySuspend {
                conversationRepository.getConversationById(any())
            }.returns(Either.Right(conversation))
        }

        suspend fun withConversationDetailsById(result: ConversationViewEntity?) = apply {
            everySuspend {
                conversationDAO.getConversationDetailsById(any())
            }.returns(result)
        }

        suspend fun withConversationById(result: ConversationEntity?) = apply {
            everySuspend {
                conversationDAO.getConversationById(any())
            }.returns(result)
        }

        suspend fun withProtocolInfoById(result: ConversationEntity.ProtocolInfo) = apply {
            everySuspend {
                conversationDAO.getConversationProtocolInfo(any())
            }.returns(result)
        }

        suspend fun withAddMemberAPISucceedChanged() = apply {
            everySuspend {
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
            everySuspend {
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
            everySuspend {
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
            everySuspend {
                conversationApi.addMember(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withDeleteMemberAPISucceedChanged() = apply {
            everySuspend {
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
            everySuspend {
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
            everySuspend {
                conversationApi.removeMember(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withFetchUsersIfUnknownByIdsSuccessful() = apply {
            everySuspend {
                userRepository.fetchUsersIfUnknownByIds(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withEmitLocalEvent() = apply {
            everySuspend {
                localEventRepository.emitLocalEvent(any())
            }.returns(Unit)
        }

        suspend fun withSuccessfulLeaveMLSGroup() = apply {
            everySuspend {
                mlsConversationRepository.leaveGroup(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulAddMemberToMLSGroup() = apply {
            everySuspend {
                mlsConversationRepository.addMemberToMLSGroup(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        /**
         * Mocks sequentially responses from [MLSConversationRepository] with [result].
         */
        suspend fun withAddingMemberToMlsGroupResults(vararg results: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                mlsConversationRepository.addMemberToMLSGroup(any(), any(), any(), any())
            } sequentiallyReturns results.asList()
        }

        suspend fun withSuccessfulRemoveMemberFromMLSGroup() = apply {
            everySuspend {
                mlsConversationRepository.removeMembersFromMLSGroup(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulCallToGenerateGuestRoomLinkApi(
            result: EventContentDTO.Conversation.CodeUpdated
        ) = apply {
            everySuspend {
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
            everySuspend {
                conversationApi.generateGuestRoomLink(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withDeleteGuestLink() = apply {
            everySuspend {
                conversationDAO.deleteGuestRoomLink(any())
            }.returns(Unit)
        }

        suspend fun withSuccessfulCallToRevokeGuestRoomLinkApi() = apply {
            everySuspend {
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
            everySuspend {
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
            everySuspend {
                conversationDAO.observeGuestRoomLinkByConversationId(any())
            }.returns(result)
        }

        suspend fun withConversationAppsAccessIfEnabled() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationAppsAccessIfEnabled(
                    any(),
                    any(),
                    any(),
                    any(),
                    any<ConversationEntity.Type>()
                )
            }.returns(Unit.right())
        }

        suspend fun withSuccessfulNewConversationGroupStartedHandled() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationStarted(any(), any(), any())
            }.returns(Either.Right(Unit))
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationStarted(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulNewConversationMemberHandled() = apply {
            everySuspend {
                newConversationMembersRepository.persistMembersAdditionToTheConversation(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateMessageTimerAPISuccess(event: EventContentDTO.Conversation.MessageTimerUpdate): Arrangement = apply {
            everySuspend {
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
            everySuspend {
                conversationApi.updateMessageTimer(any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )
        }

        suspend fun withSuccessfulHandleMessageTimerUpdateEvent() = apply {
            everySuspend {
                conversationMessageTimerEventHandler.handle(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withInsertFailedToAddSystemMessageSuccess(): Arrangement = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationFailedToAddMembers(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withAddMemberAPIFailsFirstWithUnreachableThenSucceed(networkResponses: Array<NetworkResponse<ConversationMemberAddedResponse>>) =
            apply {
                everySuspend { conversationApi.addMember(any(), any()) }
                    .sequentiallyReturns(networkResponses.asList())
            }

        suspend fun withSuccessfulNewConversationGroupStartedUnverifiedWarningHandled() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withRemoteFetchCode(
            result: NetworkResponse<ConversationInviteLinkResponse>
        ) = apply {
            everySuspend {
                conversationApi.guestLinkInfo(any())
            }.returns(result)
        }

        suspend fun withSuccessfulFetchUsersLegalHoldConsent(result: ListUsersLegalHoldConsent) = apply {
            everySuspend {
                userRepository.fetchUsersLegalHoldConsent(any())
            }.returns(Either.Right(result))
        }

        suspend fun withSuccessfulLegalHoldHandleConversationMembersChanged() = apply {
            everySuspend {
                legalHoldHandler.handleConversationMembersChanged(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withCellSystemMessages() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationCellStatus(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun arrange() = this to conversationGroupRepository
            .also {
                withMLSTransactionReturning(Either.Right(Unit))
                withTransactionReturning(Either.Right(Unit))
                withCellSystemMessages()
            }
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
            FederationError(
                FederationErrorResponse.Unreachable(
                    listOf(
                        "unstableDomain1.com",
                        "unstableDomain2.com"
                    )
                )
            )
        )

        val FEDERATION_ERROR_CONFLICTING_BACKENDS = NetworkResponse.Error(
            FederationError(
                FederationErrorResponse.Conflict(
                    listOf(
                        "conflictingDomain1.com",
                        "conflictingDomain2.com"
                    )
                )
            )
        )

        val FEDERATION_ERROR_GENERAL = NetworkResponse.Error(
            FederationError(FederationErrorResponse.Generic(422, "", "federation-remote-error", null))
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
