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

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.GroupInfoBundle
import com.wire.kalium.cryptography.GroupInfoEncryptionType
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.cryptography.RotateBundle
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.E2EI_CONVERSATION_CLIENT_INFO_ENTITY
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.TEST_FAILURE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.WIRE_IDENTITY
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mlspublickeys.Ed25519Key
import com.wire.kalium.logic.data.mlspublickeys.KeyType
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKey
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.MemberLeaveReasonDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.E2EIConversationClientInfoEntity
import com.wire.kalium.persistence.dao.message.LocalId
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSConversationRepositoryTest {

    @Test
    fun givenCommitMessage_whenDecryptingMessage_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withDecryptMLSMessageSuccessful(Arrangement.DECRYPTED_MESSAGE_BUNDLE)
            .arrange()

        val epochChange = async(TestKaliumDispatcher.default) {
            arrangement.epochsFlow.first()
        }
        yield()

        mlsConversationRepository.decryptMessage(Arrangement.COMMIT, Arrangement.GROUP_ID)
        assertEquals(Arrangement.GROUP_ID, epochChange.await())
    }

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
            .suspendFunction(arrangement.mlsClient::addMember)
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
    fun givenMlsStaleMessageError_whenCallingEstablishMLSGroup_thenAbortCommitAndWipeData() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR)
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldFail()

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::wipeConversation)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
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
            .suspendFunction(arrangement.mlsClient::addMember)
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
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenMemberJoinEventIsProcessedWithLocalId() = runTest {
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
            .with(matching { it is Event.Conversation.MemberJoin && LocalId.check(it.id) })
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
    fun givenRetryLimitIsReached_whenCallingAddMemberToMLSGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .withWaitUntilLiveSuccessful()
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
            .withMergePendingGroupFromExternalCommitSuccessful()
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::joinByExternalCommit)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::mergePendingGroupFromExternalCommit)
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
    fun givenRetryLimitIsReached_whenCallingCommitPendingProposals_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .withWaitUntilLiveSuccessful()
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
            .suspendFunction(arrangement.mlsClient::removeMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenMemberLeaveEventIsProcessedWithLocalId() = runTest {
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
            .with(matching { it is Event.Conversation.MemberLeave && LocalId.check(it.id) })
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
    fun givenRetryLimitIsReached_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .withWaitUntilLiveSuccessful()
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

    // -----

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveClientsFromGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::removeMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveClientsFromGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitPendingProposals)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingRemoveClientsFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenClientMismatchError_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withWaitUntilLiveSuccessful()
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenStaleMessageError_whenCallingRemoveClientsFromGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing(times = 1)
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .withClearProposalTimerSuccessful()
            .withWaitUntilLiveSuccessful()
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
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
    fun givenRetryLimitIsReached_whenCallingUpdateKeyMaterial_clearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .withWaitUntilLiveSuccessful()
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
            .suspendFunction(arrangement.mlsClient::conversationEpoch)
            .with(any())
            .wasInvoked(once)

    }


    @Test
    fun givenSuccessResponse_whenSendingCommitBundle_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val (_, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val epochChange = async(TestKaliumDispatcher.default) {
            mlsConversationRepository.observeEpochChanges().first()
        }
        yield()

        mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)

        assertEquals(Arrangement.GROUP_ID, epochChange.await())
    }

    @Test
    fun givenSuccessResponse_whenSendingExternalCommitBundle_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val (_, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withSendMLSMessageSuccessful()
            .withSendCommitBundleSuccessful()
            .withJoinByExternalCommitSuccessful()
            .withMergePendingGroupFromExternalCommitSuccessful()
            .arrange()

        val epochChange = async(TestKaliumDispatcher.default) {
            mlsConversationRepository.observeEpochChanges().first()
        }
        yield()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, ByteArray(0))

        assertEquals(Arrangement.GROUP_ID, epochChange.await())
    }

    @Test
    fun givenVerifiedConversation_whenGetGroupVerify_thenVerifiedReturned() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetGroupVerifyReturn(E2EIConversationState.VERIFIED)
            .arrange()

        assertEquals(
            Either.Right(Conversation.VerificationStatus.VERIFIED),
            mlsConversationRepository.getConversationVerificationStatus(Arrangement.GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenNotVerifiedConversation_whenGetGroupVerify_thenNotVerifiedReturned() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetGroupVerifyReturn(E2EIConversationState.NOT_VERIFIED)
            .arrange()

        assertEquals(
            Either.Right(Conversation.VerificationStatus.NOT_VERIFIED),
            mlsConversationRepository.getConversationVerificationStatus(Arrangement.GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenNotEnabledE2EIForConversation_whenGetGroupVerify_thenNotVerifiedReturned() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetGroupVerifyReturn(E2EIConversationState.NOT_ENABLED)
            .arrange()

        assertEquals(
            Either.Right(Conversation.VerificationStatus.NOT_VERIFIED),
            mlsConversationRepository.getConversationVerificationStatus(Arrangement.GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenNoMLSClient_whenGetGroupVerify_thenErrorReturned() = runTest {
        val failure = CoreFailure.Unknown(RuntimeException("Error!"))
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientFailed(failure)
            .withGetGroupVerifyReturn(E2EIConversationState.NOT_VERIFIED)
            .arrange()

        assertEquals(
            Either.Left(failure),
            mlsConversationRepository.getConversationVerificationStatus(Arrangement.GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenSuccessResponse_whenRotatingKeysAndMigratingConversation_thenReturnsSuccess() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withRotateAllSuccessful()
            .withSendCommitBundleSuccessful()
            .withKeyPackageLimits(10)
            .withReplaceKeyPackagesReturning(Either.Right(Unit))
            .arrange()

        assertEquals(
            Either.Right(Unit),
            mlsConversationRepository.rotateKeysAndMigrateConversations(TestClient.CLIENT_ID, arrangement.e2eiClient, "")
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiRotateAll)
            .with(any(), any(), any())
            .wasInvoked(once)

        verify(arrangement.keyPackageRepository)
            .suspendFunction(arrangement.keyPackageRepository::replaceKeyPackages)
            .with(any(), any())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)
    }

    @Test
    fun givenReplacingKeypackagesFailed_whenRotatingKeysAndMigratingConversation_thenReturnsFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withRotateAllSuccessful()
            .withKeyPackageLimits(10)
            .withReplaceKeyPackagesReturning(TEST_FAILURE)
            .withSendCommitBundleSuccessful()
            .arrange()

        assertEquals(
            TEST_FAILURE,
            mlsConversationRepository.rotateKeysAndMigrateConversations(TestClient.CLIENT_ID, arrangement.e2eiClient, "")
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiRotateAll)
            .with(any(), any(), any())
            .wasInvoked(once)

        verify(arrangement.keyPackageRepository)
            .suspendFunction(arrangement.keyPackageRepository::replaceKeyPackages)
            .with(any(), any())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendingCommitBundlesFails_whenRotatingKeysAndMigratingConversation_thenReturnsFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withRotateAllSuccessful()
            .withKeyPackageLimits(10)
            .withReplaceKeyPackagesReturning(Either.Right(Unit))
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .arrange()


        val result = mlsConversationRepository.rotateKeysAndMigrateConversations(TestClient.CLIENT_ID, arrangement.e2eiClient, "")
        result.shouldFail()

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiRotateAll)
            .with(any(), any(), any())
            .wasInvoked(once)

        verify(arrangement.keyPackageRepository)
            .suspendFunction(arrangement.keyPackageRepository::replaceKeyPackages)
            .with(any(), any())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetE2EIConversationClientInfoByClientIdSucceed_thenReturnsIdentity() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetDeviceIdentitiesReturn(listOf(WIRE_IDENTITY))
            .withGetE2EIConversationClientInfoByClientIdReturns(E2EI_CONVERSATION_CLIENT_INFO_ENTITY)
            .arrange()

        assertEquals(Either.Right(WIRE_IDENTITY), mlsConversationRepository.getClientIdentity(TestClient.CLIENT_ID))

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::getDeviceIdentities)
            .with(any(), any())
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::getE2EIConversationClientInfoByClientId)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetE2EIConversationClientInfoByClientIdFails_thenReturnsError() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetDeviceIdentitiesReturn(listOf(WIRE_IDENTITY))
            .withGetE2EIConversationClientInfoByClientIdReturns(null)
            .arrange()

        assertEquals(Either.Left(StorageFailure.DataNotFound), mlsConversationRepository.getClientIdentity(TestClient.CLIENT_ID))

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::getDeviceIdentities)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::getE2EIConversationClientInfoByClientId)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetUserIdentitiesFails_thenReturnsError() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetDeviceIdentitiesReturn(emptyList())
            .withGetE2EIConversationClientInfoByClientIdReturns(E2EI_CONVERSATION_CLIENT_INFO_ENTITY)
            .arrange()

        mlsConversationRepository.getClientIdentity(TestClient.CLIENT_ID).shouldFail()

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::getDeviceIdentities)
            .with(any(), any())
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::getE2EIConversationClientInfoByClientId)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenUserId_whenGetMLSGroupIdByUserIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = "some_group"
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetUserIdentitiesReturn(
                mapOf(
                    TestUser.USER_ID.value to listOf(WIRE_IDENTITY),
                    "some_other_user_id" to listOf(WIRE_IDENTITY.copy(clientId = "another_client_id")),
                )
            )
            .withGetMLSGroupIdByUserIdReturns(groupId)
            .arrange()

        assertEquals(Either.Right(listOf(WIRE_IDENTITY)), mlsConversationRepository.getUserIdentity(TestUser.USER_ID))

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::getUserIdentities)
            .with(eq(groupId), any())
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::getMLSGroupIdByUserId)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenConversationId_whenGetMLSGroupIdByConversationIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = "some_group"
        val member1 = TestUser.USER_ID
        val member2 = TestUser.USER_ID.copy(value = "member_2_id")
        val member3 = TestUser.USER_ID.copy(value = "member_3_id")
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetUserIdentitiesReturn(
                mapOf(
                    member1.value to listOf(WIRE_IDENTITY),
                    member2.value to listOf(WIRE_IDENTITY.copy(clientId = "member_2_client_id"))
                )
            )
            .withGetMLSGroupIdByConversationIdReturns(groupId)
            .arrange()

        assertEquals(
            Either.Right(
                mapOf(
                    member1 to listOf(WIRE_IDENTITY),
                    member2 to listOf(WIRE_IDENTITY.copy(clientId = "member_2_client_id"))
                )
            ),
            mlsConversationRepository.getMembersIdentities(TestConversation.ID, listOf(member1, member2, member3))
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::getUserIdentities)
            .with(eq(groupId), any())
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::getMLSGroupIdByConversationId)
            .with(any())
            .wasInvoked(once)
    }

    private class Arrangement {

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
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val e2eiClient = mock(classOf<E2EIClient>())

        @Mock
        val syncManager = mock(SyncManager::class)

        @Mock
        val keyPackageLimitsProvider = mock(classOf<KeyPackageLimitsProvider>())

        val epochsFlow = MutableSharedFlow<GroupID>()

        val proposalTimersFlow = MutableSharedFlow<ProposalTimer>()

        init {
            withCommitBundleEventReceiverSucceeding()
        }

        fun withCommitBundleEventReceiverSucceeding() = apply {
            given(commitBundleEventReceiver)
                .suspendFunction(commitBundleEventReceiver::onEvent)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetConversationByGroupIdSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeConversationByGroupID)
                .whenInvokedWith(anything())
                .then { flowOf(TestConversation.VIEW_ENTITY) }
        }

        fun withGetConversationByGroupIdFailing() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeConversationByGroupID)
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
        fun withKeyPackageLimits(refillAmount: Int) = apply {
            given(keyPackageLimitsProvider).function(keyPackageLimitsProvider::refillAmount)
                .whenInvoked()
                .thenReturn(refillAmount)
        }
        fun withReplaceKeyPackagesReturning(result: Either<CoreFailure, Unit>) = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::replaceKeyPackages)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
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

        fun withGetMLSClientFailed(failure: CoreFailure.Unknown) = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Left(failure) }
        }

        fun withRotateAllSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::e2eiRotateAll)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(ROTATE_BUNDLE)
        }

        fun withGetDeviceIdentitiesReturn(identities: List<WireIdentity>) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::getDeviceIdentities)
                .whenInvokedWith(anything(), anything())
                .thenReturn(identities)
        }

        fun withGetE2EIConversationClientInfoByClientIdReturns(e2eiInfo: E2EIConversationClientInfoEntity?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getE2EIConversationClientInfoByClientId)
                .whenInvokedWith(anything())
                .thenReturn(e2eiInfo)
        }

        fun withAddMLSMemberSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::addMember)
                .whenInvokedWith(anything(), anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withGetGroupEpochReturn(epoch: ULong) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::conversationEpoch)
                .whenInvokedWith(anything())
                .thenReturn(epoch)
        }

        fun withJoinConversationSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::joinConversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(COMMIT)
        }

        fun withJoinByExternalCommitSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::joinByExternalCommit)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withMergePendingGroupFromExternalCommitSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::mergePendingGroupFromExternalCommit)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
        }

        fun withProcessWelcomeMessageSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::processWelcomeMessage)
                .whenInvokedWith(anything())
                .thenReturn(RAW_GROUP_ID)
        }

        fun withCommitPendingProposalsSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::commitPendingProposals)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withCommitPendingProposalsReturningNothing(times: Int = Int.MAX_VALUE) = apply {
            withCommitPendingProposalsSuccessful()
            var invocationCounter = 0
            given(mlsClient)
                .suspendFunction(mlsClient::commitPendingProposals)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times })
                .thenReturn(null)
        }

        fun withUpdateKeyingMaterialSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::updateKeyingMaterial)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
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

        fun withDecryptMLSMessageSuccessful(decryptedMessage: com.wire.kalium.cryptography.DecryptedMessageBundle) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::decryptMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(listOf(decryptedMessage))
        }

        fun withRemoveMemberSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::removeMember)
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

        fun withGetGroupVerifyReturn(verificationStatus: E2EIConversationState) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::isGroupVerified)
                .whenInvokedWith(anything())
                .thenReturn(verificationStatus)
        }

        fun withGetMLSGroupIdByUserIdReturns(result: String?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getMLSGroupIdByUserId)
                .whenInvokedWith(anything())
                .thenReturn(result)
        }

        fun withGetMLSGroupIdByConversationIdReturns(result: String?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getMLSGroupIdByConversationId)
                .whenInvokedWith(anything())
                .thenReturn(result)
        }

        fun withGetUserIdentitiesReturn(identitiesMap: Map<String, List<WireIdentity>>) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::getUserIdentities)
                .whenInvokedWith(anything(), anything())
                .thenReturn(identitiesMap)
        }

        fun arrange() = this to MLSConversationDataSource(
            TestUser.SELF.id,
            keyPackageRepository,
            mlsClientProvider,
            mlsMessageApi,
            conversationDAO,
            clientApi,
            syncManager,
            mlsPublicKeysRepository,
            commitBundleEventReceiver,
            epochsFlow,
            proposalTimersFlow,
            keyPackageLimitsProvider
        )

        companion object {
            val TEST_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
            const val EPOCH = 5UL
            const val RAW_GROUP_ID = "groupId"
            val TIME = DateTimeUtil.currentIsoDateTimeString()
            val GROUP_ID = GroupID(RAW_GROUP_ID)
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
            val PUBLIC_GROUP_STATE_BUNDLE = GroupInfoBundle(
                GroupInfoEncryptionType.PLAINTEXT,
                RatchetTreeType.FULL,
                PUBLIC_GROUP_STATE
            )
            val COMMIT_BUNDLE = CommitBundle(COMMIT, WELCOME, PUBLIC_GROUP_STATE_BUNDLE)
            val ROTATE_BUNDLE = RotateBundle(mapOf(RAW_GROUP_ID to COMMIT_BUNDLE), emptyList(), emptyList())
            val WIRE_IDENTITY = WireIdentity("id", "user_handle", "User Test", "domain.com", "certificate", CryptoCertificateStatus.VALID, thumbprint = "thumbprint")
            val E2EI_CONVERSATION_CLIENT_INFO_ENTITY =
                E2EIConversationClientInfoEntity(UserIDEntity(uuid4().toString(), "domain.com"), "clientId", "groupId")
            val DECRYPTED_MESSAGE_BUNDLE = com.wire.kalium.cryptography.DecryptedMessageBundle(
                message = null,
                commitDelay = null,
                senderClientId = null,
                hasEpochChanged = true,
                identity = null
            )
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
                ConversationMemberRemovedDTO(emptyList(), MemberLeaveReasonDTO.LEFT),
                TestConversation.NETWORK_USER_ID1.value
            )
            val WELCOME_EVENT = Event.Conversation.MLSWelcome(
                "eventId",
                TestConversation.ID,
                false,
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
