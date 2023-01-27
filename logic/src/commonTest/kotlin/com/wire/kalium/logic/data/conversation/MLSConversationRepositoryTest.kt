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

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.PublicGroupStateBundle
import com.wire.kalium.cryptography.PublicGroupStateEncryptionType
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mlspublickeys.Ed25519Key
import com.wire.kalium.logic.data.mlspublickeys.KeyType
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKey
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationUsers
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.util.DateTimeUtil
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.anyInstanceOf
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSConversationRepositoryTest {
    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSGroup_thenGroupIsCreatedAndCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::createConversation)
            .with(eq(Arrangement.RAW_GROUP_ID), eq(listOf(Arrangement.CRYPTO_MLS_PUBLIC_KEY)))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::addMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingEstablishMLSGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withWaitUntilLiveSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.syncManager)
            .function(arrangement.syncManager::waitUntilLiveOrFailure)
            .with()
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(twice)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSGroup_thenKeyPackagesAreClaimedForMembers() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldSucceed()

        verify(arrangement.keyPackageRepository)
            .suspendFunction(arrangement.keyPackageRepository::claimKeyPackages)
            .with(matching { it.containsAll(listOf(TestConversation.USER_1)) })
            .wasInvoked(once)
    }

    @Test
    fun givenNoOtherClients_whenCallingEstablishMLSGroup_thenCommitIsCreatedByUpdatingKeyMaterial() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(keyPackages = emptyList())
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, emptyList())
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::updateKeyingMaterial)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenExistingConversation_whenCallingEstablishMLSGroupFromWelcome_thenGroupIsCreatedAndGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withProcessWelcomeMessageSuccessful()
            .withGetConversationByGroupIdSuccessful()
            .arrange()

        mlsConversationRepository.establishMLSGroupFromWelcome(Arrangement.WELCOME_EVENT).shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::processWelcomeMessage)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.ESTABLISHED), eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonExistingConversation_whenCallingEstablishMLSGroupFromWelcome_ThenGroupIsCreatedButConversationIsNotInserted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withProcessWelcomeMessageSuccessful()
            .withGetConversationByGroupIdFailing()
            .arrange()

        mlsConversationRepository.establishMLSGroupFromWelcome(Arrangement.WELCOME_EVENT).shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::processWelcomeMessage)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::addMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenMemberJoinEventIsProcessed() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful(events = listOf(Arrangement.MEMBER_JOIN_EVENT))
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.commitBundleEventReceiver)
            .suspendFunction(arrangement.commitBundleEventReceiver::onEvent)
            .with(anyInstanceOf(Event.Conversation.MemberJoin::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::commitPendingProposals)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingAddMemberToMLSGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withWaitUntilLiveSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.syncManager)
            .function(arrangement.syncManager::waitUntilLiveOrFailure)
            .with()
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(twice)
    }

    @Test
    fun givenMlsStaleMessageError_whenCallingAddMemberToMLSGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing(times = 1)
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .withClearProposalTimerSuccessful()
            .withWaitUntilLiveSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.syncManager)
            .suspendFunction(arrangement.syncManager::waitUntilLiveOrFailure)
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasNotInvoked()

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(twice)
    }

    @Test
    fun givenNonRecoverableError_whenCallingAddMemberToMLSGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRequestToJoinGroup_ThenGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withSendMLSMessageSuccessful()
            .arrange()

        val result = mlsConversationRepository.requestToJoinGroup(Arrangement.GROUP_ID, Arrangement.EPOCH)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::joinConversation)
            .with(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.EPOCH))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE), eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingJoinByExternalCommit_ThenGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withSendMLSMessageSuccessful()
            .withSendCommitBundleSuccessful()
            .withJoinByExternalCommitSuccessful()
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::joinByExternalCommit)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::mergePendingGroupFromExternalCommit)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.ESTABLISHED), eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingJoinByExternalCommit_ThenClearCommit() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withJoinByExternalCommitSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingGroupExternalCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitPendingProposals)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenProposalTimerIsClearedOnSuccess() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::clearProposalTimer)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingCommitPendingProposals_thenProposalTimerIsNotCleared() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::clearProposalTimer)
            .with(eq(Arrangement.GROUP_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenNonRecoverableError_whenCallingCommitPendingProposals_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::removeMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenMemberLeaveEventIsProcessed() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful(events = listOf(Arrangement.MEMBER_LEAVE_EVENT))
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.commitBundleEventReceiver)
            .suspendFunction(arrangement.commitBundleEventReceiver::onEvent)
            .with(anyInstanceOf(Event.Conversation.MemberLeave::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitPendingProposals)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenClientMismatchError_whenCallingRemoveMemberFromGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withSendWelcomeMessageSuccessful()
            .withWaitUntilLiveSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.syncManager)
            .function(arrangement.syncManager::waitUntilLiveOrFailure)
            .with()
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(twice)
    }

    @Test
    fun givenStaleMessageError_whenCallingRemoveMemberFromGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing(times = 1)
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .withSendWelcomeMessageSuccessful()
            .withClearProposalTimerSuccessful()
            .withWaitUntilLiveSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.syncManager)
            .suspendFunction(arrangement.syncManager::waitUntilLiveOrFailure)
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasNotInvoked()

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(twice)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenKeyingMaterialTimestampIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateKeyingMaterial)
            .with(eq(Arrangement.RAW_GROUP_ID), anyInstanceOf(Instant::class))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingUpdateKeyMaterial_clearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenConversationWithOutdatedEpoch_whenCallingIsGroupOutOfSync_returnsTrue() = runTest {
        val returnEpoch = 10UL
        val conversationEpoch = 5UL
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetGroupEpochReturn(returnEpoch)
            .arrange()

        val result = mlsConversationRepository.isGroupOutOfSync(Arrangement.GROUP_ID, conversationEpoch)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::conversationEpoch)
            .with(any())
            .wasInvoked(once)

    }

    class Arrangement {
        val idMapper: IdMapper = IdMapperImpl()

        @Mock
        val commitBundleEventReceiver = mock(classOf<CommitBundleEventReceiver>())

        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val mlsPublicKeysRepository = mock(classOf<MLSPublicKeysRepository>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val conversationDAO = mock(classOf<ConversationDAO>())

        @Mock
        val clientApi = mock(ClientApi::class)

        @Mock
        val mlsMessageApi = mock(classOf<MLSMessageApi>())

        @Mock
        val conversationApi = mock(classOf<ConversationApi>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val syncManager = mock(SyncManager::class)

        fun withGetConversationByGroupIdSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByGroupID)
                .whenInvokedWith(anything())
                .then { flowOf(TestConversation.VIEW_ENTITY) }
        }

        fun withGetConversationByGroupIdFailing() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByGroupID)
                .whenInvokedWith(anything())
                .then { flowOf(null) }
        }

        fun withClearProposalTimerSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::clearProposalTimer)
                .whenInvokedWith(anything())
                .thenDoNothing()
        }

        fun withClaimKeyPackagesSuccessful(keyPackages: List<KeyPackageDTO> = listOf(KEY_PACKAGE)) = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::claimKeyPackages)
                .whenInvokedWith(anything())
                .then { Either.Right(keyPackages) }
        }

        fun withGetPublicKeysSuccessful() = apply {
            given(mlsPublicKeysRepository)
                .suspendFunction(mlsPublicKeysRepository::getKeys)
                .whenInvoked()
                .then { Either.Right(listOf(MLS_PUBLIC_KEY)) }
        }

        fun withGetMLSClientSuccessful() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(mlsClient) }
        }

        fun withAddMLSMemberSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::addMember)
                .whenInvokedWith(anything(), anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withGetGroupEpochReturn(epoch: ULong) = apply {
            given(mlsClient)
                .function(mlsClient::conversationEpoch)
                .whenInvokedWith(anything())
                .thenReturn(epoch)
        }

        fun withJoinConversationSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::joinConversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(COMMIT)
        }

        fun withJoinByExternalCommitSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::joinByExternalCommit)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withProcessWelcomeMessageSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::processWelcomeMessage)
                .whenInvokedWith(anything())
                .thenReturn(RAW_GROUP_ID)
        }

        fun withCommitPendingProposalsSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::commitPendingProposals)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withCommitPendingProposalsReturningNothing(times: Int = Int.MAX_VALUE) = apply {
            withCommitPendingProposalsSuccessful()
            var invocationCounter = 0
            given(mlsClient)
                .function(mlsClient::commitPendingProposals)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times })
                .thenReturn(null)
        }

        fun withUpdateKeyingMaterialSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::updateKeyingMaterial)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withSendWelcomeMessageSuccessful() = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendWelcomeMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(Unit, emptyMap(), 201) }
        }

        fun withSendCommitBundleSuccessful(events: List<EventContentDTO> = emptyList()) = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendCommitBundle)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(SendMLSMessageResponse(TIME, events), emptyMap(), 201) }
        }

        fun withSendCommitBundleFailing(failure: KaliumException, times: Int = Int.MAX_VALUE) = apply {
            withSendCommitBundleSuccessful()
            var invocationCounter = 0
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendCommitBundle)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times })
                .then { NetworkResponse.Error(failure) }
        }

        fun withSendMLSMessageSuccessful(events: List<EventContentDTO> = emptyList()) = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(SendMLSMessageResponse(TIME, events), emptyMap(), 201) }
        }

        fun withRemoveMemberSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::removeMember)
                .whenInvokedWith(anything(), anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withFetchClientsOfUsersSuccessful() = apply {
            given(clientApi)
                .suspendFunction(clientApi::listClientsOfUsers)
                .whenInvokedWith(anything())
                .thenReturn(NetworkResponse.Success(value = CLIENTS_OF_USERS_RESPONSE, headers = mapOf(), httpCode = 200))
        }

        fun withWaitUntilLiveSuccessful() = apply {
            given(syncManager)
                .suspendFunction(syncManager::waitUntilLiveOrFailure)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to MLSConversationDataSource(
            keyPackageRepository,
            mlsClientProvider,
            mlsMessageApi,
            conversationDAO,
            clientApi,
            syncManager,
            mlsPublicKeysRepository,
            commitBundleEventReceiver
        )

        internal companion object {
            const val EPOCH = 5UL
            const val RAW_GROUP_ID = "groupId"
            val TIME = DateTimeUtil.currentIsoDateTimeString()
            val GROUP_ID = GroupID(RAW_GROUP_ID)
            val CONVERSATION_ID = ConversationId("ConvId", "Domain")
            val INVALID_REQUEST_ERROR = KaliumException.InvalidRequestError(ErrorResponse(405, "", ""))
            val MLS_STALE_MESSAGE_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", "mls-stale-message"))
            val MLS_CLIENT_MISMATCH_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", "mls-client-mismatch"))
            val MLS_PUBLIC_KEY = MLSPublicKey(
                Ed25519Key("gRNvFYReriXbzsGu7zXiPtS8kaTvhU1gUJEV9rdFHVw=".decodeBase64Bytes()),
                KeyType.REMOVAL
            )
            val CRYPTO_MLS_PUBLIC_KEY = MapperProvider.mlsPublicKeyMapper().toCrypto(MLS_PUBLIC_KEY)
            val KEY_PACKAGE = KeyPackageDTO(
                "client1",
                "wire.com",
                "keyPackage",
                "keyPackageRef",
                "user1"
            )
            val WELCOME = "welcome".encodeToByteArray()
            val COMMIT = "commit".encodeToByteArray()
            val PUBLIC_GROUP_STATE = "public_group_state".encodeToByteArray()
            val PUBLIC_GROUP_STATE_BUNDLE = PublicGroupStateBundle(
                PublicGroupStateEncryptionType.PLAINTEXT,
                RatchetTreeType.FULL,
                PUBLIC_GROUP_STATE
            )
            val COMMIT_BUNDLE = CommitBundle(COMMIT, WELCOME, PUBLIC_GROUP_STATE_BUNDLE)
            val MEMBER_JOIN_EVENT = EventContentDTO.Conversation.MemberJoinDTO(
                TestConversation.NETWORK_ID,
                TestConversation.NETWORK_USER_ID1,
                "2022-03-30T15:36:00.000Z",
                ConversationMembers(emptyList(), emptyList()),
                TestConversation.NETWORK_USER_ID1.value
            )
            val MEMBER_LEAVE_EVENT = EventContentDTO.Conversation.MemberLeaveDTO(
                TestConversation.NETWORK_ID,
                TestConversation.NETWORK_USER_ID1,
                "2022-03-30T15:36:00.000Z",
                ConversationUsers(emptyList(), emptyList()),
                TestConversation.NETWORK_USER_ID1.value
            )
            val WELCOME_EVENT = Event.Conversation.MLSWelcome(
                "eventId",
                TestConversation.ID,
                false,
                TestUser.USER_ID,
                WELCOME.encodeBase64(),
                timestampIso = "2022-03-30T15:36:00.000Z"
            )
            private val SIMPLE_CLIENT_RESPONSE = SimpleClientResponse("an ID", DeviceTypeDTO.Desktop)

            val CLIENTS_OF_USERS_RESPONSE = mapOf(TestUser.NETWORK_ID to listOf(SIMPLE_CLIENT_RESPONSE))
        }
    }
}
