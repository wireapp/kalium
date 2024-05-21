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
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
<<<<<<< HEAD
import com.wire.kalium.logic.di.MapperProvider
=======
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
>>>>>>> c6a9c302c2 (feat: set the correct external sender key when creating MLS conversation [WPB-8592] 🍒 (#2745))
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
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
import com.wire.kalium.util.KaliumDispatcher
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MLSConversationRepositoryTest {

    @Test
    fun givenCommitMessage_whenDecryptingMessage_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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
            val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
                .withGetMLSClientSuccessful()
                .withDecryptMLSMessageSuccessful(messageWithNewDistributionPoints)
                .withCheckRevocationListResult()
                .arrange()

            val epochChange = async(TestKaliumDispatcher.default) {
                arrangement.epochsFlow.first()
            }
            yield()

            mlsConversationRepository.decryptMessage(Arrangement.COMMIT, Arrangement.GROUP_ID)

            coVerify {
                arrangement.checkRevocationList.check(any())
            }.wasInvoked(once)

            coVerify {
                arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
            }.wasInvoked(once)

            assertEquals(Arrangement.GROUP_ID, epochChange.await())
        }

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSGroup_thenGroupIsCreatedAndCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.createConversation(Arrangement.RAW_GROUP_ID, Arrangement.CRYPTO_MLS_PUBLIC_KEY)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.addMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.commitAccepted(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenPartialKeyClaimingResponses_whenCallingEstablishMLSGroup_thenMissingKeyPackagesFailureIsReturned() = runTest {
        val userMissingKeyPackage = TestUser.USER_ID.copy(value = "missingKP")
        val usersMissingKeyPackages = setOf(userMissingKeyPackage)
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(usersWithoutKeyPackages = usersMissingKeyPackages)
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
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

        coVerify {
            arrangement.mlsClient.createConversation(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.CRYPTO_MLS_PUBLIC_KEY))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.addMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsClient.commitAccepted(eq(Arrangement.RAW_GROUP_ID))
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsClient.wipeConversation(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenPartialKeyClaimingResponsesAndAllowPartial_whenCallingEstablishMLSGroup_thenPartialGroupCreatedAndSuccessReturned() = runTest {
        val userMissingKeyPackage = TestUser.USER_ID.copy(value = "missingKP")
        val userWithKeyPackage = TestConversation.USER_1
        val usersMissingKeyPackages = setOf(userMissingKeyPackage)
        val usersWithKeyPackages = setOf(userWithKeyPackage)
        val keyPackageSuccess = KEY_PACKAGE.copy(userId = userWithKeyPackage.value, domain = userWithKeyPackage.domain)
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(keyPackages = listOf(keyPackageSuccess), usersWithoutKeyPackages = usersMissingKeyPackages)
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
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

        coVerify {
            arrangement.mlsClient.createConversation(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.CRYPTO_MLS_PUBLIC_KEY))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.addMember(eq(Arrangement.RAW_GROUP_ID), matches { it.size == usersWithKeyPackages.size })
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsClient.commitAccepted(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsClient.wipeConversation(eq(Arrangement.RAW_GROUP_ID))
        }.wasNotInvoked()
    }

    @Test
    fun givenNewCrlDistributionPoints_whenEstablishingMLSGroup_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

    }

    @Test
    fun givenNewCrlDistributionPoints_whenAddingMemberToMLSGroup_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful(COMMIT_BUNDLE.copy(crlNewDistributionPoints = listOf("url")))
            .withCheckRevocationListResult()
            .withSendCommitBundleSuccessful()
            .arrange()

        mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))

        coVerify {
            arrangement.checkRevocationList.check(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingEstablishMLSGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withWaitUntilLiveSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)

        coVerify {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(twice)
    }

    @Test
    fun givenMlsStaleMessageError_whenCallingEstablishMLSGroup_thenAbortCommitAndWipeData() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR)
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldFail()

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.clearPendingCommit(Arrangement.RAW_GROUP_ID)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.wipeConversation(Arrangement.RAW_GROUP_ID)
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSGroup_thenKeyPackagesAreClaimedForMembers() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_1))
        result.shouldSucceed()

        coVerify {
            arrangement.keyPackageRepository.claimKeyPackages(matches { it.containsAll(listOf(TestConversation.USER_1)) })
        }.wasInvoked(once)
    }

    @Test
    fun givenNoOtherClients_whenCallingEstablishMLSGroup_thenCommitIsCreatedByUpdatingKeyMaterial() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(keyPackages = emptyList())
            .withGetMLSClientSuccessful()
            .withKeyForCipherSuite()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID, emptyList())
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.updateKeyingMaterial(Arrangement.RAW_GROUP_ID)
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.addMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.commitAccepted(Arrangement.RAW_GROUP_ID)
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenMemberJoinEventIsProcessedWithLocalId() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful(events = listOf(Arrangement.MEMBER_JOIN_EVENT))
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        coVerify {
            arrangement.commitBundleEventReceiver.onEvent(
                matches { it is Event.Conversation.MemberJoin && LocalId.check(it.id) },
                any()
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsSuccessful()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingAddMemberToMLSGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withWaitUntilLiveSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)

        coVerify {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(twice)
    }

    @Test
    fun givenMlsStaleMessageError_whenCallingAddMemberToMLSGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(twice)
    }

    @Test
    fun givenNonRecoverableError_whenCallingAddMemberToMLSGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingAddMemberToMLSGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRequestToJoinGroup_ThenGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withSendMLSMessageSuccessful()
            .arrange()

        val result = mlsConversationRepository.requestToJoinGroup(Arrangement.GROUP_ID, Arrangement.EPOCH)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.joinConversation(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.EPOCH))
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.updateConversationGroupState(
                eq(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE),
                eq(Arrangement.RAW_GROUP_ID)
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingJoinByExternalCommit_ThenGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withSendMLSMessageSuccessful()
            .withSendCommitBundleSuccessful()
            .withJoinByExternalCommitSuccessful()
            .withMergePendingGroupFromExternalCommitSuccessful()
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        coVerify {
            arrangement.mlsClient.joinByExternalCommit(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.mergePendingGroupFromExternalCommit(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.updateConversationGroupState(
                eq(ConversationEntity.GroupState.ESTABLISHED),
                eq(Arrangement.RAW_GROUP_ID)
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.checkRevocationList.check(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMlsClientReturnsNewCrlDistributionPoints_whenJoiningGroupByExternalCommit_thenCheckRevocationList() = runTest {
        val commitBundleWithDistributionPoints = COMMIT_BUNDLE.copy(crlNewDistributionPoints = listOf("url"))
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withCheckRevocationListResult()
            .withSendMLSMessageSuccessful()
            .withSendCommitBundleSuccessful()
            .withJoinByExternalCommitSuccessful(commitBundleWithDistributionPoints)
            .withMergePendingGroupFromExternalCommitSuccessful()
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        coVerify {
            arrangement.checkRevocationList.check(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingJoinByExternalCommit_ThenClearCommit() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withJoinByExternalCommitSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        coVerify {
            arrangement.mlsClient.clearPendingGroupExternalCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.commitAccepted(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenProposalTimerIsClearedOnSuccess() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.conversationDAO.clearProposalTimer(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingCommitPendingProposals_thenProposalTimerIsNotCleared() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        coVerify {
            arrangement.conversationDAO.clearProposalTimer(Arrangement.GROUP_ID.value)
        }.wasNotInvoked()
    }

    @Test
    fun givenNonRecoverableError_whenCallingCommitPendingProposals_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingCommitPendingProposals_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .withWaitUntilLiveSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.removeMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenMemberLeaveEventIsProcessedWithLocalId() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful(events = listOf(Arrangement.MEMBER_LEAVE_EVENT))
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        coVerify {
            arrangement.commitBundleEventReceiver.onEvent(matches { it is Event.Conversation.MemberLeave && LocalId.check(it.id) }, any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenClientMismatchError_whenCallingRemoveMemberFromGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)

        coVerify {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(twice)
    }

    @Test
    fun givenStaleMessageError_whenCallingRemoveMemberFromGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.clearPendingCommit(Arrangement.RAW_GROUP_ID)
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(twice)
    }

    // -----

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveClientsFromGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.removeMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveClientsFromGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingRemoveClientsFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(Arrangement.GROUP_ID, clients)
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenClientMismatchError_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenStaleMessageError_whenCallingRemoveClientsFromGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(twice)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsClient.commitAccepted(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenKeyingMaterialTimestampIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.conversationDAO.updateKeyingMaterial(eq(Arrangement.RAW_GROUP_ID), any<Instant>())
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingUpdateKeyMaterial_clearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendCommitBundleFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingUpdateKeyMaterial_clearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendCommitBundleFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .withWaitUntilLiveSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.clearPendingCommit(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenConversationWithOutdatedEpoch_whenCallingIsGroupOutOfSync_returnsTrue() = runTest {
        val returnEpoch = 10UL
        val conversationEpoch = 5UL
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withGetGroupEpochReturn(returnEpoch)
            .arrange()

        val result = mlsConversationRepository.isGroupOutOfSync(Arrangement.GROUP_ID, conversationEpoch)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsClient.conversationEpoch(any())
        }.wasInvoked(once)

    }


    @Test
    fun givenSuccessResponse_whenSendingCommitBundle_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val (_, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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
        val (_, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.e2eiRotateAll(any(), any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.keyPackageRepository.replaceKeyPackages(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)

        coVerify {
            arrangement.checkRevocationList.check(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNewDistributionsCRL_whenRotatingKeys_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.checkRevocationList.check(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenReplacingKeypackagesFailed_whenRotatingKeysAndMigratingConversation_thenReturnsFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.e2eiRotateAll(any(), any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.keyPackageRepository.replaceKeyPackages(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasNotInvoked()
    }

    @Test
    fun givenSendingCommitBundlesFails_whenRotatingKeysAndMigratingConversation_thenReturnsFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withRotateAllSuccessful()
            .withKeyPackageLimits(10)
            .withReplaceKeyPackagesReturning(Either.Right(Unit))
            .withSendCommitBundleFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .arrange()


        val result = mlsConversationRepository.rotateKeysAndMigrateConversations(TestClient.CLIENT_ID, arrangement.e2eiClient, "")
        result.shouldFail()

        coVerify {
            arrangement.mlsClient.e2eiRotateAll(any(), any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.keyPackageRepository.replaceKeyPackages(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
        }.wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetE2EIConversationClientInfoByClientIdSucceed_thenReturnsIdentity() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withGetDeviceIdentitiesReturn(listOf(WIRE_IDENTITY))
            .withGetE2EIConversationClientInfoByClientIdReturns(E2EI_CONVERSATION_CLIENT_INFO_ENTITY)
            .arrange()

        assertEquals(Either.Right(WIRE_IDENTITY), mlsConversationRepository.getClientIdentity(TestClient.CLIENT_ID))

        coVerify {
            arrangement.mlsClient.getDeviceIdentities(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.getE2EIConversationClientInfoByClientId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetE2EIConversationClientInfoByClientIdFails_thenReturnsError() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withGetDeviceIdentitiesReturn(listOf(WIRE_IDENTITY))
            .withGetE2EIConversationClientInfoByClientIdReturns(null)
            .arrange()

        assertEquals(Either.Left(StorageFailure.DataNotFound), mlsConversationRepository.getClientIdentity(TestClient.CLIENT_ID))

        coVerify {
            arrangement.mlsClient.getDeviceIdentities(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationDAO.getE2EIConversationClientInfoByClientId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetUserIdentitiesEmpty_thenReturnsNull() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSClientSuccessful()
            .withGetDeviceIdentitiesReturn(emptyList())
            .withGetE2EIConversationClientInfoByClientIdReturns(E2EI_CONVERSATION_CLIENT_INFO_ENTITY)
            .arrange()

        assertEquals(Either.Right(null), mlsConversationRepository.getClientIdentity(TestClient.CLIENT_ID))

        coVerify {
            arrangement.mlsClient.getDeviceIdentities(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.getE2EIConversationClientInfoByClientId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSelfUserId_whenGetMLSGroupIdByUserIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = TestConversation.MLS_PROTOCOL_INFO.groupId.value
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.getUserIdentities(eq(groupId), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.getMLSGroupIdByUserId(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationDAO.getEstablishedSelfMLSGroupId()
        }.wasInvoked(once)
    }

    @Test
    fun givenOtherUserId_whenGetMLSGroupIdByUserIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = TestConversation.MLS_PROTOCOL_INFO.groupId.value
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.getUserIdentities(eq(groupId), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.conversationDAO.getMLSGroupIdByUserId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenConversationId_whenGetMLSGroupIdByConversationIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = "some_group"
        val member1 = TestUser.USER_ID
        val member2 = TestUser.USER_ID.copy(value = "member_2_id")
        val member3 = TestUser.USER_ID.copy(value = "member_3_id")
        val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
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

        coVerify {
            arrangement.mlsClient.getUserIdentities(eq(groupId), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.getMLSGroupIdByConversationId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSSubConversationGroup_thenGroupIsCreatedAndCommitBundleIsSentAndAccepted() =
        runTest {
            val (arrangement, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
                .withCommitPendingProposalsReturningNothing()
                .withClaimKeyPackagesSuccessful()
                .withGetMLSClientSuccessful()
                .withGetMLSGroupIdByConversationIdReturns(Arrangement.GROUP_ID.value)
                .withGetExternalSenderKeySuccessful()
                .withKeyForCipherSuite()
                .withUpdateKeyingMaterialSuccessful()
                .withSendCommitBundleSuccessful()
                .arrange()

            val result = mlsConversationRepository.establishMLSSubConversationGroup(Arrangement.GROUP_ID, TestConversation.ID)
            result.shouldSucceed()

            coVerify {
                arrangement.mlsClient.createConversation(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.EXTERNAL_SENDER_KEY.value))
            }.wasInvoked(once)

            coVerify {
                arrangement.mlsMessageApi.sendCommitBundle(any<MLSMessageApi.CommitBundle>())
            }.wasInvoked(once)

            coVerify {
                arrangement.mlsClient.commitAccepted(eq(Arrangement.RAW_GROUP_ID))
            }.wasInvoked(once)
        }

    @Test
    fun givenHandleWithSchemeAndDomain_whenGetUserIdentity_thenHandleShouldReturnProperValues() = runTest {
        // given
        val scheme = "wireapp"
        val handle = "handle"
        val domain = "domain.com"
        val handleWithSchemeAndDomain = "$scheme://%40$handle@$domain"
        val groupId = Arrangement.GROUP_ID.value
        val wireIdentity = WIRE_IDENTITY.copy(
            certificate = WIRE_IDENTITY.certificate!!.copy(
                handle = WireIdentity.Handle.fromString(handleWithSchemeAndDomain, domain)
            )
        )
        val (_, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetEstablishedSelfMLSGroupIdReturns(groupId)
            .withGetMLSClientSuccessful()
            .withGetUserIdentitiesReturn(mapOf(groupId to listOf(wireIdentity)))
            .arrange()
        // when
        val result = mlsConversationRepository.getUserIdentity(TestUser.USER_ID)
        // then
        result.shouldSucceed() {
            it.forEach {
                assertEquals(scheme, it.certificate?.handle?.scheme)
                assertEquals(handle, it.certificate?.handle?.handle)
                assertEquals(domain, it.certificate?.handle?.domain)
            }
        }
    }

    @Test
    fun givenHandleWithSchemeAndDomain_whenGetMemberIdentities_thenHandleShouldReturnProperValues() = runTest {
        // given
        val scheme = "wireapp"
        val handle = "handle"
        val domain = "domain.com"
        val handleWithSchemeAndDomain = "$scheme://%40$handle@$domain"
        val groupId = Arrangement.GROUP_ID.value
        val wireIdentity = WIRE_IDENTITY.copy(
            certificate = WIRE_IDENTITY.certificate!!.copy(
                handle = WireIdentity.Handle.fromString(handleWithSchemeAndDomain, domain)
            )
        )
        val (_, mlsConversationRepository) = Arrangement(testKaliumDispatcher)
            .withGetMLSGroupIdByConversationIdReturns(groupId)
            .withGetMLSClientSuccessful()
            .withGetUserIdentitiesReturn(mapOf(groupId to listOf(wireIdentity)))
            .arrange()
        // when
        val result = mlsConversationRepository.getMembersIdentities(TestConversation.ID, listOf(TestUser.USER_ID))
        // then
        result.shouldSucceed() {
            it.values.forEach {
                it.forEach {
                    assertEquals(scheme, it.certificate?.handle?.scheme)
                    assertEquals(handle, it.certificate?.handle?.handle)
                    assertEquals(domain, it.certificate?.handle?.domain)
                }
            }
        }
    }

    private class Arrangement(
        var kaliumDispatcher: KaliumDispatcher = TestKaliumDispatcher
    ) {

        @Mock
        val commitBundleEventReceiver = mock(CommitBundleEventReceiver::class)

        @Mock
        val keyPackageRepository = mock(KeyPackageRepository::class)

        @Mock
        val mlsPublicKeysRepository = mock(MLSPublicKeysRepository::class)

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val conversationDAO = mock(ConversationDAO::class)

        @Mock
        val clientApi = mock(ClientApi::class)

        @Mock
        val mlsMessageApi = mock(MLSMessageApi::class)

        @Mock
        val mlsClient = mock(MLSClient::class)

        @Mock
        val e2eiClient = mock(E2EIClient::class)

        @Mock
        val syncManager = mock(SyncManager::class)

        @Mock
        val keyPackageLimitsProvider = mock(KeyPackageLimitsProvider::class)

        @Mock
        val checkRevocationList = mock(RevocationListChecker::class)

        @Mock
        val certificateRevocationListRepository = mock(CertificateRevocationListRepository::class)

        val epochsFlow = MutableSharedFlow<GroupID>()

        val proposalTimersFlow = MutableSharedFlow<ProposalTimer>()

        suspend fun arrange() = this to MLSConversationDataSource(
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
            kaliumDispatcher = kaliumDispatcher
        ).also {
            withCommitBundleEventReceiverSucceeding()
        }

        suspend fun withCommitBundleEventReceiverSucceeding() = apply {
            coEvery {
                commitBundleEventReceiver.onEvent(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withClearProposalTimerSuccessful() = apply {
            coEvery { conversationDAO.clearProposalTimer(any()) }
                .returns(Unit)
        }

        suspend fun withClaimKeyPackagesSuccessful(
            keyPackages: List<KeyPackageDTO> = listOf(KEY_PACKAGE),
            usersWithoutKeyPackages: Set<UserId> = setOf()
        ) = apply {
            coEvery {
                keyPackageRepository.claimKeyPackages(any())
            }.returns(Either.Right(KeyPackageClaimResult(keyPackages, usersWithoutKeyPackages)))
        }

        fun withKeyPackageLimits(refillAmount: Int) = apply {
            every {
                keyPackageLimitsProvider.refillAmount()
            }.returns(refillAmount)
        }

        suspend fun withReplaceKeyPackagesReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                keyPackageRepository.replaceKeyPackages(any(), any())
            }.returns(result)
        }

        suspend fun withGetPublicKeysSuccessful() = apply {
            coEvery {
                mlsPublicKeysRepository.getKeys()
            }.returns(Either.Right(MLS_PUBLIC_KEY))
        }

        suspend fun withKeyForCipherSuite() = apply {
            coEvery {
                mlsPublicKeysRepository.getKeyForCipherSuite(any())
            }.returns(Either.Right(CRYPTO_MLS_PUBLIC_KEY))
        }

        suspend fun withGetMLSClientSuccessful() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))
        }

        suspend fun withGetExternalSenderKeySuccessful() = apply {
            coEvery {
                mlsClient.getExternalSenders(any())
            }.returns(EXTERNAL_SENDER_KEY)
        }

        suspend fun withRotateAllSuccessful(rotateBundle: RotateBundle = ROTATE_BUNDLE) = apply {
            coEvery {
                mlsClient.e2eiRotateAll(any(), any(), any())
            }.returns(rotateBundle)
        }

        suspend fun withGetDeviceIdentitiesReturn(identities: List<WireIdentity>) = apply {
            coEvery {
                mlsClient.getDeviceIdentities(any(), any())
            }.returns(identities)
        }

        suspend fun withGetE2EIConversationClientInfoByClientIdReturns(e2eiInfo: E2EIConversationClientInfoEntity?) = apply {
            coEvery {
                conversationDAO.getE2EIConversationClientInfoByClientId(any())
            }.returns(e2eiInfo)
        }

        suspend fun withGetEstablishedSelfMLSGroupIdReturns(id: String?) = apply {
            coEvery {
                conversationDAO.getEstablishedSelfMLSGroupId()
            }.returns(id)
        }

        suspend fun withAddMLSMemberSuccessful(commitBundle: CommitBundle = COMMIT_BUNDLE) = apply {
            coEvery {
                mlsClient.addMember(any(), any())
            }.returns(commitBundle)
        }

        suspend fun withGetGroupEpochReturn(epoch: ULong) = apply {
            coEvery {
                mlsClient.conversationEpoch(any())
            }.returns(epoch)
        }

        suspend fun withJoinConversationSuccessful() = apply {
            coEvery {
                mlsClient.joinConversation(any(), any())
            }.returns(COMMIT)
        }

        suspend fun withJoinByExternalCommitSuccessful(commitBundle: CommitBundle = COMMIT_BUNDLE) = apply {
            coEvery {
                mlsClient.joinByExternalCommit(any())
            }.returns(commitBundle)
        }

        suspend fun withMergePendingGroupFromExternalCommitSuccessful() = apply {
            coEvery {
                mlsClient.mergePendingGroupFromExternalCommit(any())
            }.returns(Unit)
        }

        suspend fun withCommitPendingProposalsSuccessful() = apply {
            coEvery {
                mlsClient.commitPendingProposals(any())
            }.returns(COMMIT_BUNDLE)
        }

        suspend fun withCommitPendingProposalsReturningNothing(times: Int = Int.MAX_VALUE) = apply {
            withCommitPendingProposalsSuccessful()
            var invocationCounter = 0
            coEvery {
                mlsClient.commitPendingProposals(matches { invocationCounter += 1; invocationCounter <= times })
            }.returns(null)
        }

        suspend fun withUpdateKeyingMaterialSuccessful(commitBundle: CommitBundle = COMMIT_BUNDLE) = apply {
            coEvery {
                mlsClient.updateKeyingMaterial(any())
            }.returns(commitBundle)
        }

        suspend fun withSendCommitBundleSuccessful(events: List<EventContentDTO> = emptyList()) = apply {
            coEvery {
                mlsMessageApi.sendCommitBundle(any())
            }.returns(NetworkResponse.Success(SendMLSMessageResponse(TIME, events), emptyMap(), 201))
        }

        suspend fun withSendCommitBundleFailing(failure: KaliumException, times: Int = Int.MAX_VALUE) = apply {
            withSendCommitBundleSuccessful()
            var invocationCounter = 0
            coEvery {
                mlsMessageApi.sendCommitBundle(matches { invocationCounter += 1; invocationCounter <= times })
            }.returns(NetworkResponse.Error(failure))
        }

        suspend fun withCheckRevocationListResult() = apply {
            coEvery {
                checkRevocationList.check(any())
            }.returns(Either.Right(1uL))
        }

        suspend fun withSendMLSMessageSuccessful(events: List<EventContentDTO> = emptyList()) = apply {
            coEvery {
                mlsMessageApi.sendMessage(any())
            }.returns(NetworkResponse.Success(SendMLSMessageResponse(TIME, events), emptyMap(), 201))
        }

        suspend fun withDecryptMLSMessageSuccessful(decryptedMessage: com.wire.kalium.cryptography.DecryptedMessageBundle) = apply {
            coEvery {
                mlsClient.decryptMessage(any(), any())
            }.returns(listOf(decryptedMessage))
        }

        suspend fun withRemoveMemberSuccessful() = apply {
            coEvery {
                mlsClient.removeMember(any(), any())
            }.returns(COMMIT_BUNDLE)
        }

        suspend fun withFetchClientsOfUsersSuccessful() = apply {
            coEvery {
                clientApi.listClientsOfUsers(any())
            }.returns(NetworkResponse.Success(value = CLIENTS_OF_USERS_RESPONSE, headers = mapOf(), httpCode = 200))
        }

        suspend fun withWaitUntilLiveSuccessful() = apply {
            coEvery {
                syncManager.waitUntilLiveOrFailure()
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetMLSGroupIdByUserIdReturns(result: String?) = apply {
            coEvery {
                conversationDAO.getMLSGroupIdByUserId(any())
            }.returns(result)
        }

        suspend fun withGetMLSGroupIdByConversationIdReturns(result: String?) = apply {
            coEvery {
                conversationDAO.getMLSGroupIdByConversationId(any())
            }.returns(result)
        }

        suspend fun withGetUserIdentitiesReturn(identitiesMap: Map<String, List<WireIdentity>>) = apply {
            coEvery {
                mlsClient.getUserIdentities(any(), any())
            }.returns(identitiesMap)
        }

        fun withGetDefaultCipherSuite(cipherSuite: CipherSuite) = apply {
            every {
                mlsClient.getDefaultCipherSuite()
            }.returns(cipherSuite.tag.toUShort())
        }

        companion object {
            val TEST_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
            const val EPOCH = 5UL
            const val RAW_GROUP_ID = "groupId"
            val GROUP_ID = GroupID(RAW_GROUP_ID)
            val TIME = DateTimeUtil.currentIsoDateTimeString()
            val INVALID_REQUEST_ERROR = KaliumException.InvalidRequestError(ErrorResponse(405, "", ""))
            val MLS_STALE_MESSAGE_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", "mls-stale-message"))
            val MLS_CLIENT_MISMATCH_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", "mls-client-mismatch"))
            val MLS_PUBLIC_KEY = MLSPublicKeys(
                removal = mapOf(
                    "ed25519" to "gRNvFYReriXbzsGu7zXiPtS8kaTvhU1gUJEV9rdFHVw="
                )
            )

            val CRYPTO_MLS_PUBLIC_KEY: ByteArray = MLS_PUBLIC_KEY.removal?.get("ed25519")!!.decodeBase64Bytes()
            val KEY_PACKAGE = KeyPackageDTO(
                "client1",
                "wire.com",
                "keyPackage",
                "keyPackageRef",
                "user1"
            )
            val WELCOME = "welcome".encodeToByteArray()
            val EXTERNAL_SENDER_KEY = ExternalSenderKey("externalSenderKey".encodeToByteArray())
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
                    endTimestampSeconds = 1899105093
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
