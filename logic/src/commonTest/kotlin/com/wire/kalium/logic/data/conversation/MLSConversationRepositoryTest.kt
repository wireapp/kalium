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
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.ExternalSenderKey
import com.wire.kalium.cryptography.GroupInfoBundle
import com.wire.kalium.cryptography.GroupInfoEncryptionType
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.cryptography.RotateBundle
import com.wire.kalium.cryptography.WelcomeBundle
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.COMMIT_BUNDLE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.CRYPTO_CLIENT_ID
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.E2EI_CONVERSATION_CLIENT_INFO_ENTITY
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.KEY_PACKAGE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.ROTATE_BUNDLE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.TEST_FAILURE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.WIRE_IDENTITY
import com.wire.kalium.logic.data.conversation.mls.KeyPackageClaimResult
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mlspublickeys.Ed25519Key
import com.wire.kalium.logic.data.mlspublickeys.KeyType
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKey
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
    fun givenCommitMessageWithNewDistributionPoints_whenDecryptingMessage_thenCheckRevocationList() =
        runTest(TestKaliumDispatcher.default) {
            val messageWithNewDistributionPoints = Arrangement.DECRYPTED_MESSAGE_BUNDLE.copy(
                crlNewDistributionPoints = listOf("url")
            )
            val (arrangement, mlsConversationRepository) = Arrangement()
                .withGetMLSClientSuccessful()
                .withDecryptMLSMessageSuccessful(messageWithNewDistributionPoints)
                .withCheckRevocationListResult()
                .arrange()

            val epochChange = async(TestKaliumDispatcher.default) {
                arrangement.epochsFlow.first()
            }
            yield()

            mlsConversationRepository.decryptMessage(Arrangement.COMMIT, Arrangement.GROUP_ID)

            verify(arrangement.checkRevocationList)
                .suspendFunction(arrangement.checkRevocationList::invoke)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.certificateRevocationListRepository)
                .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
                .with(any(), any())
                .wasInvoked(once)

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
    fun givenPartialKeyClaimingResponses_whenCallingEstablishMLSGroup_thenMissingKeyPackagesFailureIsReturned() = runTest {
        val userMissingKeyPackage = TestUser.USER_ID.copy(value = "missingKP")
        val usersMissingKeyPackages = setOf(userMissingKeyPackage)
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(usersWithoutKeyPackages = usersMissingKeyPackages)
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            Arrangement.GROUP_ID,
            listOf(TestConversation.USER_1, userMissingKeyPackage)
        )
        result.shouldFail {
            assertIs<CoreFailure.MissingKeyPackages>(it)
            assertEquals(usersMissingKeyPackages, it.failedUserIds)
        }

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::createConversation)
            .with(eq(Arrangement.RAW_GROUP_ID), eq(listOf(Arrangement.CRYPTO_MLS_PUBLIC_KEY)))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::addMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasNotInvoked()

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasNotInvoked()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasNotInvoked()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::wipeConversation)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenPartialKeyClaimingResponsesAndAllowPartial_whenCallingEstablishMLSGroup_thenPartialGroupCreatedAndSuccessReturned() = runTest {
        val userMissingKeyPackage = TestUser.USER_ID.copy(value = "missingKP")
        val userWithKeyPackage = TestConversation.USER_1
        val usersMissingKeyPackages = setOf(userMissingKeyPackage)
        val usersWithKeyPackages = setOf(userWithKeyPackage)
        val keyPackageSuccess = KEY_PACKAGE.copy(userId = userWithKeyPackage.value, domain = userWithKeyPackage.domain)
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(keyPackages = listOf(keyPackageSuccess), usersWithoutKeyPackages = usersMissingKeyPackages)
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            Arrangement.GROUP_ID,
            (usersWithKeyPackages + userMissingKeyPackage).toList(),
            allowSkippingUsersWithoutKeyPackages = true
        )
        result.shouldSucceed {
            assertEquals(usersMissingKeyPackages, it.notAddedUsers)
            assertEquals(usersWithKeyPackages, it.successfullyAddedUsers)
        }

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::createConversation)
            .with(eq(Arrangement.RAW_GROUP_ID), eq(listOf(Arrangement.CRYPTO_MLS_PUBLIC_KEY)))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::addMember)
            .with(eq(Arrangement.RAW_GROUP_ID), matching { it.size == usersWithKeyPackages.size })
            .wasInvoked(exactly = once)

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendCommitBundle)
            .with(anyInstanceOf(MLSMessageApi.CommitBundle::class))
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::wipeConversation)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenNewCrlDistributionPoints_whenEstablishingMLSGroup_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

    }

    @Test
    fun givenNewCrlDistributionPoints_whenAddingMemberToMLSGroup_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withAddMLSMemberSuccessful(COMMIT_BUNDLE.copy(crlNewDistributionPoints = listOf("url")))
            .withCheckRevocationListResult()
            .withSendCommitBundleSuccessful()
            .arrange()

        mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
            .with(any(), any())
            .wasInvoked(exactly = once)
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

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMlsClientReturnsNewCrlDistributionPoints_whenJoiningGroupByExternalCommit_thenCheckRevocationList() = runTest {
        val commitBundleWithDistributionPoints = COMMIT_BUNDLE.copy(crlNewDistributionPoints = listOf("url"))
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withCheckRevocationListResult()
            .withSendMLSMessageSuccessful()
            .withSendCommitBundleSuccessful()
            .withJoinByExternalCommitSuccessful(commitBundleWithDistributionPoints)
            .withMergePendingGroupFromExternalCommitSuccessful()
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
            .with(any(), any())
            .wasInvoked(exactly = once)
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
            Arrangement.GROUP_ID
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
            Arrangement.GROUP_ID
        }
        yield()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, ByteArray(0))

        assertEquals(Arrangement.GROUP_ID, epochChange.await())
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

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenNewDistributionsCRL_whenRotatingKeys_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withRotateAllSuccessful(ROTATE_BUNDLE.copy(crlNewDistributionPoints = listOf("url")))
            .withSendCommitBundleSuccessful()
            .withKeyPackageLimits(10)
            .withReplaceKeyPackagesReturning(Either.Right(Unit))
            .withCheckRevocationListResult()
            .arrange()

        assertEquals(
            Either.Right(Unit),
            mlsConversationRepository.rotateKeysAndMigrateConversations(TestClient.CLIENT_ID, arrangement.e2eiClient, "")
        )

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
            .with(any(), any())
            .wasInvoked(exactly = once)
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
            E2EIFailure.RotationAndMigration(TEST_FAILURE.value).left(),
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
    fun givenGetClientId_whenGetUserIdentitiesEmpty_thenReturnsNull() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetDeviceIdentitiesReturn(emptyList())
            .withGetE2EIConversationClientInfoByClientIdReturns(E2EI_CONVERSATION_CLIENT_INFO_ENTITY)
            .arrange()

        assertEquals(Either.Right(null), mlsConversationRepository.getClientIdentity(TestClient.CLIENT_ID))

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
    fun givenSelfUserId_whenGetMLSGroupIdByUserIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = TestConversation.MLS_PROTOCOL_INFO.groupId.value
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetEstablishedSelfMLSGroupIdReturns(groupId)
            .withGetUserIdentitiesReturn(
                mapOf(
                    TestUser.USER_ID.value to listOf(WIRE_IDENTITY),
                    "some_other_user_id" to listOf(WIRE_IDENTITY.copy(clientId = CRYPTO_CLIENT_ID.copy("another_client_id"))),
                )
            )
            .arrange()

        assertEquals(Either.Right(listOf(WIRE_IDENTITY)), mlsConversationRepository.getUserIdentity(TestUser.USER_ID))

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::getUserIdentities)
            .with(eq(groupId), any())
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::getMLSGroupIdByUserId)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::getEstablishedSelfMLSGroupId)
            .wasInvoked(once)
    }

    @Test
    fun givenOtherUserId_whenGetMLSGroupIdByUserIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = TestConversation.MLS_PROTOCOL_INFO.groupId.value
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetUserIdentitiesReturn(
                mapOf(
                    TestUser.OTHER_USER_ID.value to listOf(WIRE_IDENTITY),
                    "some_other_user_id" to listOf(WIRE_IDENTITY.copy(clientId = CRYPTO_CLIENT_ID.copy("another_client_id"))),
                )
            )
            .withGetMLSGroupIdByUserIdReturns(groupId)
            .arrange()

        assertEquals(Either.Right(listOf(WIRE_IDENTITY)), mlsConversationRepository.getUserIdentity(TestUser.OTHER_USER_ID))

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
                    member2.value to listOf(WIRE_IDENTITY.copy(clientId = CRYPTO_CLIENT_ID.copy("member_2_client_id")))
                )
            )
            .withGetMLSGroupIdByConversationIdReturns(groupId)
            .arrange()

        assertEquals(
            Either.Right(
                mapOf(
                    member1 to listOf(WIRE_IDENTITY),
                    member2 to listOf(WIRE_IDENTITY.copy(clientId = CRYPTO_CLIENT_ID.copy("member_2_client_id")))
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

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSSubConversationGroup_thenGroupIsCreatedAndCommitBundleIsSentAndAccepted() =
        runTest {
            val (arrangement, mlsConversationRepository) = Arrangement()
                .withCommitPendingProposalsReturningNothing()
                .withClaimKeyPackagesSuccessful()
                .withGetMLSClientSuccessful()
                .withGetMLSGroupIdByConversationIdReturns(Arrangement.GROUP_ID.value)
                .withGetExternalSenderKeySuccessful()
                .withGetPublicKeysSuccessful()
                .withUpdateKeyingMaterialSuccessful()
                .withSendCommitBundleSuccessful()
                .arrange()

            val result = mlsConversationRepository.establishMLSSubConversationGroup(Arrangement.GROUP_ID, TestConversation.ID)
            result.shouldSucceed()

            verify(arrangement.mlsClient)
                .function(arrangement.mlsClient::createConversation)
                .with(eq(Arrangement.RAW_GROUP_ID), eq(listOf(Arrangement.CRYPTO_MLS_EXTERNAL_KEY)))
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
    fun givenHandleWithSchemeAndDomain_whenGetUserIdentity_thenHandleWithoutSchemeAtSignAndDomainShouldReturnProperValue() = runTest {
        // given
        val handleWithSchemeAndDomain = "wireapp://%40handle@domain.com"
        val handle = "handle"
        val groupId = Arrangement.GROUP_ID.value
        val (_, mlsConversationRepository) = Arrangement()
            .withGetEstablishedSelfMLSGroupIdReturns(groupId)
            .withGetMLSClientSuccessful()
            .withGetUserIdentitiesReturn(mapOf(groupId to listOf(WIRE_IDENTITY.copy(handle = handleWithSchemeAndDomain))))
            .arrange()
        // when
        val result = mlsConversationRepository.getUserIdentity(TestUser.USER_ID)
        // then
        result.shouldSucceed() {
            it.forEach {
                assertEquals(handle, it.handleWithoutSchemeAtSignAndDomain)
            }
        }
    }

    @Test
    fun givenHandleWithSchemeAndDomain_whenGetMemberIdentities_thenHandleWithoutSchemeAtSignAndDomainShouldReturnProperValue() = runTest {
        // given
        val handleWithSchemeAndDomain = "wireapp://%40handle@domain.com"
        val handle = "handle"
        val groupId = Arrangement.GROUP_ID.value
        val (_, mlsConversationRepository) = Arrangement()
            .withGetMLSGroupIdByConversationIdReturns(groupId)
            .withGetMLSClientSuccessful()
            .withGetUserIdentitiesReturn(mapOf(groupId to listOf(WIRE_IDENTITY.copy(handle = handleWithSchemeAndDomain))))
            .arrange()
        // when
        val result = mlsConversationRepository.getMembersIdentities(TestConversation.ID, listOf(TestUser.USER_ID))
        // then
        result.shouldSucceed() {
            it.values.forEach {
                it.forEach {
                    assertEquals(handle, it.handleWithoutSchemeAtSignAndDomain)
                }
            }
        }
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

        @Mock
        val checkRevocationList = mock(classOf<CheckRevocationListUseCase>())

        @Mock
        val certificateRevocationListRepository = mock(classOf<CertificateRevocationListRepository>())

        val epochsFlow = MutableSharedFlow<GroupID>()

        val proposalTimersFlow = MutableSharedFlow<ProposalTimer>()

        val serverConfigLink = newServerConfig(1).links

        init {
            withCommitBundleEventReceiverSucceeding()
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
            keyPackageLimitsProvider,
            checkRevocationList,
            certificateRevocationListRepository,
            serverConfigLink
        )

        fun withCommitBundleEventReceiverSucceeding() = apply {
            given(commitBundleEventReceiver)
                .suspendFunction(commitBundleEventReceiver::onEvent)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withClearProposalTimerSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::clearProposalTimer)
                .whenInvokedWith(anything())
                .thenDoNothing()
        }

        fun withClaimKeyPackagesSuccessful(
            keyPackages: List<KeyPackageDTO> = listOf(KEY_PACKAGE),
            usersWithoutKeyPackages: Set<UserId> = setOf()
        ) = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::claimKeyPackages)
                .whenInvokedWith(anything())
                .then { Either.Right(KeyPackageClaimResult(keyPackages, usersWithoutKeyPackages)) }
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
        fun withGetExternalSenderKeySuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::getExternalSenders)
                .whenInvokedWith(anything())
                .thenReturn(EXTERNAL_SENDER_KEY)
        }
        fun withRotateAllSuccessful(rotateBundle: RotateBundle = ROTATE_BUNDLE) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::e2eiRotateAll)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(rotateBundle)
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

        fun withGetEstablishedSelfMLSGroupIdReturns(id: String?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getEstablishedSelfMLSGroupId)
                .whenInvoked()
                .thenReturn(id)
        }

        fun withAddMLSMemberSuccessful(commitBundle: CommitBundle = COMMIT_BUNDLE) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::addMember)
                .whenInvokedWith(anything(), anything())
                .thenReturn(commitBundle)
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

        fun withJoinByExternalCommitSuccessful(commitBundle: CommitBundle = COMMIT_BUNDLE) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::joinByExternalCommit)
                .whenInvokedWith(anything())
                .thenReturn(commitBundle)
        }

        fun withMergePendingGroupFromExternalCommitSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::mergePendingGroupFromExternalCommit)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
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

        fun withUpdateKeyingMaterialSuccessful(commitBundle: CommitBundle = COMMIT_BUNDLE) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::updateKeyingMaterial)
                .whenInvokedWith(anything())
                .thenReturn(commitBundle)
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

        fun withCheckRevocationListResult() = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(1uL))
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

        companion object {
            val TEST_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
            const val EPOCH = 5UL
            const val RAW_GROUP_ID = "groupId"
            val GROUP_ID = GroupID(RAW_GROUP_ID)
            val WELCOME_BUNDLE = WelcomeBundle(RAW_GROUP_ID, null)
            val TIME = DateTimeUtil.currentIsoDateTimeString()
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
            val EXTERNAL_SENDER_KEY = ExternalSenderKey("externalSenderKey".encodeToByteArray())
            val CRYPTO_MLS_EXTERNAL_KEY = MapperProvider.mlsPublicKeyMapper().toCrypto(EXTERNAL_SENDER_KEY)
            val COMMIT = "commit".encodeToByteArray()
            val PUBLIC_GROUP_STATE = "public_group_state".encodeToByteArray()
            val PUBLIC_GROUP_STATE_BUNDLE = GroupInfoBundle(
                GroupInfoEncryptionType.PLAINTEXT,
                RatchetTreeType.FULL,
                PUBLIC_GROUP_STATE
            )
            val COMMIT_BUNDLE = CommitBundle(COMMIT, WELCOME, PUBLIC_GROUP_STATE_BUNDLE, null)
            val ROTATE_BUNDLE = RotateBundle(mapOf(RAW_GROUP_ID to COMMIT_BUNDLE), emptyList(), emptyList(), null)
            val CRYPTO_CLIENT_ID = CryptoQualifiedClientId("clientId", TestConversation.USER_1.toCrypto())
            val WIRE_IDENTITY =
                WireIdentity(
                    CRYPTO_CLIENT_ID,
                    "user_handle",
                    "User Test",
                    "domain.com",
                    "certificate",
                    CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint",
                    serialNumber = "serialNumber",
                    endTimestamp = 1899105093
                )
            val E2EI_CONVERSATION_CLIENT_INFO_ENTITY =
                E2EIConversationClientInfoEntity(UserIDEntity(uuid4().toString(), "domain.com"), "clientId", "groupId")
            val DECRYPTED_MESSAGE_BUNDLE = com.wire.kalium.cryptography.DecryptedMessageBundle(
                message = null,
                commitDelay = null,
                senderClientId = null,
                hasEpochChanged = true,
                identity = null,
                crlNewDistributionPoints = null
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
                TestUser.USER_ID,
                WELCOME.encodeBase64(),
                timestampIso = "2022-03-30T15:36:00.000Z"
            )
            private val SIMPLE_CLIENT_RESPONSE = SimpleClientResponse("an ID", DeviceTypeDTO.Desktop)

            val CLIENTS_OF_USERS_RESPONSE = mapOf(TestUser.NETWORK_ID to listOf(SIMPLE_CLIENT_RESPONSE))
        }
    }
}
