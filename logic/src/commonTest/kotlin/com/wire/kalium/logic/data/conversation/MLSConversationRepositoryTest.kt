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
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.cryptography.CredentialType
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
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.CIPHER_SUITE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.CRYPTO_CLIENT_ID
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.E2EI_CONVERSATION_CLIENT_INFO_ENTITY
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.KEY_PACKAGE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.MLS_CLIENT_MISMATCH_ERROR
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.MLS_PUBLIC_KEY
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.TEST_FAILURE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.WELCOME_BUNDLE
import com.wire.kalium.logic.data.conversation.MLSConversationRepositoryTest.Arrangement.Companion.WIRE_IDENTITY
import com.wire.kalium.logic.data.conversation.mls.KeyPackageClaimResult
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.MemberLeaveReasonDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.E2EIConversationClientInfoEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MLSConversationRepositoryTest {

    @Test
    fun givenCommitMessageWithNewDistributionPoints_whenDecryptingMessage_thenCheckRevocationList() =
        runTest(TestKaliumDispatcher.default) {
            val messageWithNewDistributionPoints = Arrangement.DECRYPTED_MESSAGE_BUNDLE.copy(
                crlNewDistributionPoints = listOf("url")
            )
            val (arrangement, mlsConversationRepository) = Arrangement()
                .withDecryptMLSMessageSuccessful(messageWithNewDistributionPoints)
                .withCheckRevocationListResult()
                .arrange()

            mlsConversationRepository.decryptMessage(arrangement.mlsContext, Arrangement.COMMIT, Arrangement.GROUP_ID)

            coVerify {
                arrangement.checkRevocationList.check(any(), any())
            }.wasInvoked(once)

            coVerify {
                arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
            }.wasInvoked(once)
        }

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSGroup_thenGroupIsCreatedAndCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            arrangement.mlsContext,
            Arrangement.GROUP_ID,
            listOf(TestConversation.USER_1),
            publicKeys = null
        )
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.createConversation(Arrangement.RAW_GROUP_ID, Arrangement.CRYPTO_MLS_PUBLIC_KEY)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsContext.addMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenPartialKeyClaimingResponses_whenCallingEstablishMLSGroup_thenMissingKeyPackagesFailureIsReturned() = runTest {
        val userMissingKeyPackage = TestUser.USER_ID.copy(value = "missingKP")
        val usersMissingKeyPackages = setOf(userMissingKeyPackage)
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(usersWithoutKeyPackages = usersMissingKeyPackages)
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            arrangement.mlsContext,
            Arrangement.GROUP_ID,
            listOf(TestConversation.USER_1, userMissingKeyPackage)
        )
        result.shouldFail {
            assertIs<CoreFailure.MissingKeyPackages>(it)
            assertEquals(usersMissingKeyPackages, it.failedUserIds)
        }

        coVerify {
            arrangement.mlsContext.createConversation(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.CRYPTO_MLS_PUBLIC_KEY))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsContext.addMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsContext.wipeConversation(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenPartialKeyClaimingResponsesAndAllowPartial_whenCallingEstablishMLSGroup_thenPartialGroupCreatedAndSuccessReturned() = runTest {
        val userMissingKeyPackage = TestUser.USER_ID.copy(value = "missingKP")
        val userWithKeyPackage = TestConversation.USER_1
        val usersMissingKeyPackages = setOf(userMissingKeyPackage)
        val usersWithKeyPackages = setOf(userWithKeyPackage)
        val keyPackageSuccess = KEY_PACKAGE.copy(userId = userWithKeyPackage.value, domain = userWithKeyPackage.domain)
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(keyPackages = listOf(keyPackageSuccess), usersWithoutKeyPackages = usersMissingKeyPackages)
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            arrangement.mlsContext,
            Arrangement.GROUP_ID,
            (usersWithKeyPackages + userMissingKeyPackage).toList(),
            allowSkippingUsersWithoutKeyPackages = true
        )
        result.shouldSucceed {
            assertEquals(usersMissingKeyPackages, it.notAddedUsers)
            assertEquals(usersWithKeyPackages, it.successfullyAddedUsers)
        }

        coVerify {
            arrangement.mlsContext.createConversation(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.CRYPTO_MLS_PUBLIC_KEY))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsContext.addMember(eq(Arrangement.RAW_GROUP_ID), matches { it.size == usersWithKeyPackages.size })
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsContext.wipeConversation(eq(Arrangement.RAW_GROUP_ID))
        }.wasNotInvoked()
    }

    @Test
    fun givenPublicKeysIsNotNull_whenCallingEstablishMLSGroup_ThenGetPublicKeysRepositoryNotCalled() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result =
            mlsConversationRepository.establishMLSGroup(
                arrangement.mlsContext,
                Arrangement.GROUP_ID,
                listOf(TestConversation.USER_1),
                publicKeys = MLS_PUBLIC_KEY
            )
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.createConversation(
                groupId = eq(Arrangement.RAW_GROUP_ID),
                externalSenders = any()
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsContext.addMember(
                groupId = eq(Arrangement.RAW_GROUP_ID),
                membersKeyPackages = any()
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsPublicKeysRepository.getKeyForCipherSuite(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenPublicKeysIsNull_whenCallingEstablishMLSGroup_ThenGetPublicKeysRepositoryIsCalled() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result =
            mlsConversationRepository.establishMLSGroup(
                arrangement.mlsContext,
                Arrangement.GROUP_ID,
                listOf(TestConversation.USER_1),
                publicKeys = null
            )
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.createConversation(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsContext.addMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsPublicKeysRepository.getKeyForCipherSuite(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenNewCrlDistributionPoints_whenEstablishingMLSGroup_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful(listOf("url"))
            .withCheckRevocationListResult()
            .arrange()

        val result =
            mlsConversationRepository.establishMLSGroup(
                arrangement.mlsContext,
                Arrangement.GROUP_ID,
                listOf(TestConversation.USER_1),
                publicKeys = MLS_PUBLIC_KEY
            )
        result.shouldSucceed()

        coVerify {
            arrangement.checkRevocationList.check(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenNewCrlDistributionPoints_whenAddingMemberToMLSGroup_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful(crlNewDistributionPoints = listOf("url"))
            .withCheckRevocationListResult()
            .arrange()

        mlsConversationRepository.addMemberToMLSGroup(
            arrangement.mlsContext,
            Arrangement.GROUP_ID,
            listOf(TestConversation.USER_ID1),
            CIPHER_SUITE
        )

        coVerify {
            arrangement.checkRevocationList.check(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingEstablishMLSGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberThrowing(MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            mlsContext = arrangement.mlsContext,
            groupID = Arrangement.GROUP_ID,
            members = listOf(TestConversation.USER_1),
            publicKeys = null
        )
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.addMember(any(), any())
        }.wasInvoked(twice)
    }

    @Test
    fun givenMlsStaleMessageError_whenCallingEstablishMLSGroup_thenAbortCommitAndWipeData() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberThrowing(Exception(Arrangement.MLS_STALE_MESSAGE_ERROR.message))
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            arrangement.mlsContext,
            Arrangement.GROUP_ID,
            listOf(TestConversation.USER_1),
            publicKeys = null
        )
        result.shouldFail()

        coVerify {
            arrangement.mlsContext.addMember(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsContext.wipeConversation(Arrangement.RAW_GROUP_ID)
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSGroup_thenKeyPackagesAreClaimedForMembers() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withKeyForCipherSuite()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(
            arrangement.mlsContext,
            Arrangement.GROUP_ID,
            listOf(TestConversation.USER_1),
            publicKeys = null
        )
        result.shouldSucceed()

        coVerify {
            arrangement.keyPackageRepository.claimKeyPackages(
                matches {
                    it.containsAll(listOf(TestConversation.USER_1))
                },
                eq(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenNoOtherClients_whenCallingEstablishMLSGroup_thenCommitIsCreatedByUpdatingKeyMaterial() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuite(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(keyPackages = emptyList())
            .withKeyForCipherSuite()
            .withUpdateKeyingMaterialSuccessful()
            .arrange()

        val result =
            mlsConversationRepository.establishMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, emptyList(), publicKeys = null)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.updateKeyingMaterial(Arrangement.RAW_GROUP_ID)
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1), CIPHER_SUITE)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.addMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withClaimKeyPackagesSuccessful()
            .withAddMLSMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1), CIPHER_SUITE)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingAddMemberToMLSGroup_thenRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withAddMLSMemberThrowing(MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1), CIPHER_SUITE)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.addMember(any(), any())
        }.wasInvoked(twice)
    }

    @Test
    fun givenNonRecoverableError_whenCallingAddMemberToMLSGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withClaimKeyPackagesSuccessful()
            .withAddMLSMemberThrowing(Arrangement.INVALID_REQUEST_ERROR, times = 1)
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1), CIPHER_SUITE)
        result.shouldFail()
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingAddMemberToMLSGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withClaimKeyPackagesSuccessful()
            .withAddMLSMemberThrowing(Arrangement.INVALID_REQUEST_ERROR, times = Int.MAX_VALUE)
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1), CIPHER_SUITE)
        result.shouldFail()
    }

    @Test
    fun givenSuccessfulResponses_whenCallingJoinByExternalCommit_ThenGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withJoinByExternalCommitSuccessful()
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(arrangement.mlsContext, Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        coVerify {
            arrangement.mlsContext.joinByExternalCommit(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.updateConversationGroupState(
                eq(ConversationEntity.GroupState.ESTABLISHED),
                eq(Arrangement.RAW_GROUP_ID)
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.checkRevocationList.check(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMlsClientReturnsNewCrlDistributionPoints_whenJoiningGroupByExternalCommit_thenCheckRevocationList() = runTest {
        val welcomeBundleWithDistributionPoints = WELCOME_BUNDLE.copy(crlNewDistributionPoints = listOf("url"))
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCheckRevocationListResult()
            .withJoinByExternalCommitSuccessful(welcomeBundleWithDistributionPoints)
            .arrange()

        mlsConversationRepository.joinGroupByExternalCommit(arrangement.mlsContext, Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)

        coVerify {
            arrangement.checkRevocationList.check(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingJoinByExternalCommit_ThenReturnFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withJoinByExternalCommitThrowing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.joinGroupByExternalCommit(arrangement.mlsContext, Arrangement.GROUP_ID, Arrangement.PUBLIC_GROUP_STATE)
        result.shouldFail()
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenProposalTimerIsClearedOnSuccess() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.conversationDAO.clearProposalTimer(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingCommitPendingProposals_thenProposalTimerIsNotCleared() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsThrowing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldFail()

        coVerify {
            arrangement.conversationDAO.clearProposalTimer(Arrangement.GROUP_ID.value)
        }.wasNotInvoked()
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingCommitPendingProposals_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsThrowing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldFail()
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withRemoveMemberSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.removeMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withRemoveMemberSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberThrowing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, users)
        result.shouldFail()
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberThrowing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, users)
        result.shouldFail()
    }

    @Test
    fun givenClientMismatchError_whenCallingRemoveMemberFromGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberThrowing(MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.removeMember(any(), any())
        }.wasInvoked(twice)
    }

    @Test
    fun givenStaleMessageError_whenCallingRemoveMemberFromGroup_thenRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing(times = 1)
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberThrowing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .withClearProposalTimerSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.removeMember(any(), any())
        }.wasInvoked(twice)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveClientsFromGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))

        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withRemoveMemberSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withMembers(clients.map {
                CryptoQualifiedClientId(
                    it.clientId.value,
                    it.userId.toCrypto()
                )
            })
            .arrange()

        val result = mlsConversationRepository.removeClientsFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.removeMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenEmptyMemberList_whenCallingRemoveClientsFromGroup_thenRemoveClientsIsNotTriggered() = runTest {
        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))

        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withRemoveMemberSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withMembers(listOf())
            .arrange()

        val result = mlsConversationRepository.removeClientsFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.removeMember(eq(Arrangement.RAW_GROUP_ID), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveClientsFromGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))

        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withRemoveMemberSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withMembers(clients.map {
                CryptoQualifiedClientId(
                    it.clientId.value,
                    it.userId.toCrypto()
                )
            })
            .arrange()

        val result = mlsConversationRepository.removeClientsFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.commitPendingProposals(eq(Arrangement.RAW_GROUP_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingRemoveClientsFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberThrowing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, clients)
        result.shouldFail()
    }

    @Test
    fun givenClientMismatchError_whenCallingRemoveMemberFromGroup_thenFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberThrowing(MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .arrange()

        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))
        val result = mlsConversationRepository.removeClientsFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, clients)
        result.shouldFail()
    }

    @Test
    fun givenStaleMessageError_whenCallingRemoveClientsFromGroup_thenWaitUntilLiveAndRetry() = runTest {
        val clients = listOf(QualifiedClientID(ClientId("client_a"), TestUser.USER_ID))

        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing(times = 1)
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberThrowing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .withClearProposalTimerSuccessful()
            .withMembers(clients.map {
                CryptoQualifiedClientId(
                    it.clientId.value,
                    it.userId.toCrypto()
                )
            })
            .arrange()

        val result = mlsConversationRepository.removeClientsFromMLSGroup(arrangement.mlsContext, Arrangement.GROUP_ID, clients)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.removeMember(any(), any())
        }.wasInvoked(twice)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateKeyingMaterialSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.updateKeyingMaterial(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenKeyingMaterialTimestampIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateKeyingMaterialSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldSucceed()

        coVerify {
            arrangement.conversationDAO.updateKeyingMaterial(eq(Arrangement.RAW_GROUP_ID), any<Instant>())
        }.wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingUpdateKeyMaterial_clearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateKeyingMaterialThrowing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldFail()
    }

    @Test
    fun givenRetryLimitIsReached_whenCallingUpdateKeyMaterial_clearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateKeyingMaterialSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withUpdateKeyingMaterialThrowing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = Int.MAX_VALUE)
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(arrangement.mlsContext, Arrangement.GROUP_ID)
        result.shouldFail()
    }

    @Test
    fun givenConversationWithOutdatedEpoch_whenCallingIsGroupOutOfSync_returnsTrue() = runTest {
        val returnEpoch = 10UL
        val conversationEpoch = 5UL
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetGroupEpochReturn(returnEpoch)
            .arrange()

        val result = mlsConversationRepository.isGroupOutOfSync(arrangement.mlsContext, Arrangement.GROUP_ID, conversationEpoch)
        result.shouldSucceed()

        coVerify {
            arrangement.mlsContext.conversationEpoch(any())
        }.wasInvoked(once)

    }

    @Test
    fun givenSuccessResponse_whenSendingCommitBundle_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateKeyingMaterialSuccessful()
            .arrange()

        val epochChange = async(TestKaliumDispatcher.default) {
            Arrangement.GROUP_ID
        }
        yield()

        mlsConversationRepository.updateKeyingMaterial(arrangement.mlsContext, Arrangement.GROUP_ID)

        assertEquals(Arrangement.GROUP_ID, epochChange.await())
    }

    @Test
    fun givenSuccessResponse_whenSendingExternalCommitBundle_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withJoinByExternalCommitSuccessful()
            .arrange()

        val epochChange = async(TestKaliumDispatcher.default) {
            Arrangement.GROUP_ID
        }
        yield()

        mlsConversationRepository.joinGroupByExternalCommit(arrangement.mlsContext, Arrangement.GROUP_ID, ByteArray(0))

        assertEquals(Arrangement.GROUP_ID, epochChange.await())
    }

    @Test
    fun givenSuccessResponse_whenRotatingKeysAndMigratingConversation_thenReturnsSuccess() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuiteSuccessful()
            .withRotateGroupsSuccessful()
            .withSaveX509CredentialsSuccessful(listOf())
            .withKeyPackageLimits(10)
            .withGenerateKeyPackageSuccessful(listOf())
            .withReplaceKeyPackagesReturning(Either.Right(Unit))
            .arrange()

        assertEquals(
            Either.Right(Unit),
            mlsConversationRepository.rotateKeysAndMigrateConversations(
                arrangement.mlsContext,
                TestClient.CLIENT_ID,
                arrangement.e2eiClient,
                "",
                listOf(Arrangement.GROUP_ID)
            )
        )

        coVerify {
            arrangement.mlsContext.e2eiRotateGroups(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.keyPackageRepository.replaceKeyPackages(any(), any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.checkRevocationList.check(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.mlsContext.removeStaleKeyPackages()
        }.wasInvoked(once)
    }

    @Test
    fun givenNewDistributionsCRL_whenRotatingKeys_thenCheckRevocationList() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuiteSuccessful()
            .withRotateGroupsSuccessful()
            .withSaveX509CredentialsSuccessful(listOf("url"))
            .withKeyPackageLimits(10)
            .withGenerateKeyPackageSuccessful(listOf())
            .withReplaceKeyPackagesReturning(Either.Right(Unit))
            .withCheckRevocationListResult()
            .withRemoveStaleKeyPackages()
            .arrange()

        val result = mlsConversationRepository.rotateKeysAndMigrateConversations(
            mlsContext = arrangement.mlsContext,
            clientId = TestClient.CLIENT_ID,
            e2eiClient = arrangement.e2eiClient,
            certificateChain = "",
            groupIdList = listOf(Arrangement.GROUP_ID)
        )

        result.shouldSucceed()

        coVerify {
            arrangement.checkRevocationList.check(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenReplacingKeypackagesFailed_whenRotatingKeysAndMigratingConversation_thenReturnsFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuiteSuccessful()
            .withRotateGroupsSuccessful()
            .withKeyPackageLimits(10)
            .withGenerateKeyPackageSuccessful(listOf())
            .withSaveX509CredentialsSuccessful(listOf())
            .withReplaceKeyPackagesReturning(TEST_FAILURE)
            .arrange()

        assertEquals(
            E2EIFailure.RotationAndMigration(TEST_FAILURE.value).left(),
            mlsConversationRepository.rotateKeysAndMigrateConversations(
                arrangement.mlsContext,
                TestClient.CLIENT_ID,
                arrangement.e2eiClient,
                "",
                listOf(Arrangement.GROUP_ID)
            )
        )

        coVerify {
            arrangement.mlsContext.e2eiRotateGroups(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.keyPackageRepository.replaceKeyPackages(any(), any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSendingCommitBundlesFails_whenRotatingKeysAndMigratingConversation_thenReturnsFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDefaultCipherSuiteSuccessful()
            .withRotateGroupsThrowing(MLS_CLIENT_MISMATCH_ERROR)
            .withSaveX509CredentialsSuccessful(listOf())
            .withKeyPackageLimits(10)
            .withReplaceKeyPackagesReturning(Either.Right(Unit))
            .arrange()


        val result = mlsConversationRepository.rotateKeysAndMigrateConversations(
            arrangement.mlsContext,
            TestClient.CLIENT_ID,
            arrangement.e2eiClient,
            "",
            listOf()
        )
        result.shouldFail()

        coVerify {
            arrangement.mlsContext.e2eiRotateGroups(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.keyPackageRepository.replaceKeyPackages(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenGetClientId_whenGetE2EIConversationClientInfoByClientIdSucceed_thenReturnsIdentity() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDeviceIdentitiesReturn(listOf(WIRE_IDENTITY))
            .withGetE2EIConversationClientInfoByClientIdReturns(E2EI_CONVERSATION_CLIENT_INFO_ENTITY)
            .arrange()

        assertEquals(Either.Right(WIRE_IDENTITY), mlsConversationRepository.getClientIdentity(arrangement.mlsContext, TestClient.CLIENT_ID))

        coVerify {
            arrangement.mlsContext.getDeviceIdentities(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.getE2EIConversationClientInfoByClientId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetE2EIConversationClientInfoByClientIdFails_thenReturnsError() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDeviceIdentitiesReturn(listOf(WIRE_IDENTITY))
            .withGetE2EIConversationClientInfoByClientIdReturns(null)
            .arrange()

        assertEquals(
            Either.Left(StorageFailure.DataNotFound),
            mlsConversationRepository.getClientIdentity(arrangement.mlsContext, TestClient.CLIENT_ID)
        )

        coVerify {
            arrangement.mlsContext.getDeviceIdentities(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationDAO.getE2EIConversationClientInfoByClientId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenGetClientId_whenGetUserIdentitiesEmpty_thenReturnsNull() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetDeviceIdentitiesReturn(emptyList())
            .withGetE2EIConversationClientInfoByClientIdReturns(E2EI_CONVERSATION_CLIENT_INFO_ENTITY)
            .arrange()

        assertEquals(Either.Right(null), mlsConversationRepository.getClientIdentity(arrangement.mlsContext, TestClient.CLIENT_ID))

        coVerify {
            arrangement.mlsContext.getDeviceIdentities(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.getE2EIConversationClientInfoByClientId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSelfUserId_whenGetMLSGroupIdByUserIdSucceed_thenReturnsIdentities() = runTest {
        val groupId = TestConversation.MLS_PROTOCOL_INFO.groupId.value
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetEstablishedSelfMLSGroupIdReturns(groupId)
            .withGetUserIdentitiesReturn(
                mapOf(
                    TestUser.USER_ID.value to listOf(WIRE_IDENTITY),
                    "some_other_user_id" to listOf(WIRE_IDENTITY.copy(clientId = CRYPTO_CLIENT_ID.copy("another_client_id"))),
                )
            )
            .arrange()

        assertEquals(
            Either.Right(listOf(WIRE_IDENTITY)),
            mlsConversationRepository.getUserIdentity(arrangement.mlsContext, TestUser.USER_ID)
        )

        coVerify {
            arrangement.mlsContext.getUserIdentities(eq(groupId), any())
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
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetUserIdentitiesReturn(
                mapOf(
                    TestUser.OTHER_USER_ID.value to listOf(WIRE_IDENTITY),
                    "some_other_user_id" to listOf(WIRE_IDENTITY.copy(clientId = CRYPTO_CLIENT_ID.copy("another_client_id"))),
                )
            )
            .withGetMLSGroupIdByUserIdReturns(groupId)
            .arrange()

        assertEquals(
            Either.Right(listOf(WIRE_IDENTITY)),
            mlsConversationRepository.getUserIdentity(arrangement.mlsContext, TestUser.OTHER_USER_ID)
        )

        coVerify {
            arrangement.mlsContext.getUserIdentities(eq(groupId), any())
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
        val (arrangement, mlsConversationRepository) = Arrangement()
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
            mlsConversationRepository.getMembersIdentities(arrangement.mlsContext, TestConversation.ID, listOf(member1, member2, member3))
        )

        coVerify {
            arrangement.mlsContext.getUserIdentities(eq(groupId), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationDAO.getMLSGroupIdByConversationId(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSSubConversationGroup_thenGroupIsCreatedAndCommitBundleIsSentAndAccepted() =
        runTest {
            val defaultCipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

            val (arrangement, mlsConversationRepository) = Arrangement()
                .withCommitPendingProposalsReturningNothing()
                .withClaimKeyPackagesSuccessful(emptyList()) // empty cause members is empty in case of establishMLSSubConversationGroup
                .withGetMLSGroupIdByConversationIdReturns(Arrangement.GROUP_ID.value)
                .withGetExternalSenderKeySuccessful()
                .withKeyForCipherSuite()
                .withUpdateKeyingMaterialSuccessful()
                .withGetDefaultCipherSuite(defaultCipherSuite)
                .arrange()

            val result = mlsConversationRepository.establishMLSSubConversationGroup(
                arrangement.mlsContext,
                Arrangement.GROUP_ID,
                TestConversation.ID
            )
            result.shouldSucceed()

            coVerify {
                arrangement.mlsContext.createConversation(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.EXTERNAL_SENDER_KEY.value))
            }.wasInvoked(once)

            coVerify {
                arrangement.mlsContext.updateKeyingMaterial(any())
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
            x509Identity = WIRE_IDENTITY.x509Identity!!.copy(
                handle = WireIdentity.Handle.fromString(handleWithSchemeAndDomain, domain)
            )
        )
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetEstablishedSelfMLSGroupIdReturns(groupId)
            .withGetUserIdentitiesReturn(mapOf(groupId to listOf(wireIdentity)))
            .arrange()
        // when
        val result = mlsConversationRepository.getUserIdentity(arrangement.mlsContext, TestUser.USER_ID)
        // then
        result.shouldSucceed() {
            it.forEach {
                assertEquals(scheme, it.x509Identity?.handle?.scheme)
                assertEquals(handle, it.x509Identity?.handle?.handle)
                assertEquals(domain, it.x509Identity?.handle?.domain)
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
            x509Identity = WIRE_IDENTITY.x509Identity!!.copy(
                handle = WireIdentity.Handle.fromString(handleWithSchemeAndDomain, domain)
            )
        )
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSGroupIdByConversationIdReturns(groupId)
            .withGetUserIdentitiesReturn(mapOf(groupId to listOf(wireIdentity)))
            .arrange()
        // when
        val result = mlsConversationRepository.getMembersIdentities(arrangement.mlsContext, TestConversation.ID, listOf(TestUser.USER_ID))
        // then
        result.shouldSucceed() {
            it.values.forEach {
                it.forEach {
                    assertEquals(scheme, it.x509Identity?.handle?.scheme)
                    assertEquals(handle, it.x509Identity?.handle?.handle)
                    assertEquals(domain, it.x509Identity?.handle?.domain)
                }
            }
        }
    }

    @Test
    fun givenMlsCommitMissingReferencesError_whenEstablishMLSSubConversationGroup_thenShouldDiscardAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(emptyList())
            .withGetMLSGroupIdByConversationIdReturns(Arrangement.GROUP_ID.value)
            .withGetExternalSenderKeySuccessful()
            .withGetDefaultCipherSuiteSuccessful()
            .withKeyForCipherSuite()
            .withUpdateKeyingMaterialThrowing(Arrangement.MLS_COMMIT_MISSING_REFERENCES_ERROR, times = 1)
            .arrange()

        val result =
            mlsConversationRepository.establishMLSSubConversationGroup(arrangement.mlsContext, Arrangement.GROUP_ID, TestConversation.ID)

        result.shouldSucceed()

        coVerify { arrangement.mlsContext.updateKeyingMaterial(any()) }.wasInvoked(2) // Retry should occur
    }

    @Test
    fun givenStaleMessageError_whenUpdateKeyingMaterial_thenShouldKeepAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withClaimKeyPackagesSuccessful(emptyList())
            .withGetMLSGroupIdByConversationIdReturns(Arrangement.GROUP_ID.value)
            .withGetExternalSenderKeySuccessful()
            .withKeyForCipherSuite()
            .withGetDefaultCipherSuiteSuccessful()
            .withUpdateKeyingMaterialThrowing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(arrangement.mlsContext, Arrangement.GROUP_ID)

        result.shouldSucceed()

        coVerify { arrangement.mlsContext.updateKeyingMaterial(any()) }.wasInvoked(2) // Retry should occur
    }

    @Test
    fun givenUnexpectedError_whenEstablishMLSSubConversationGroup_thenShouldAbort() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful(emptyList())
            .withGetDefaultCipherSuiteSuccessful()
            .withGetMLSGroupIdByConversationIdReturns(Arrangement.GROUP_ID.value)
            .withGetExternalSenderKeySuccessful()
            .withKeyForCipherSuite()
            .withUpdateKeyingMaterialSuccessful()
            .withUpdateKeyingMaterialThrowing(Arrangement.INVALID_REQUEST_ERROR, times = 1)
            .arrange()

        val result =
            mlsConversationRepository.establishMLSSubConversationGroup(arrangement.mlsContext, Arrangement.GROUP_ID, TestConversation.ID)

        result.shouldFail()

        coVerify { arrangement.mlsContext.updateKeyingMaterial(any()) }.wasInvoked(1) // No retry should happen
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val keyPackageRepository = mock(KeyPackageRepository::class)
        val mlsPublicKeysRepository = mock(MLSPublicKeysRepository::class)
        val conversationDAO = mock(ConversationDAO::class)
        val clientApi = mock(ClientApi::class)
        val e2eiClient = mock(E2EIClient::class)
        val keyPackageLimitsProvider = mock(KeyPackageLimitsProvider::class)
        val checkRevocationList = mock(RevocationListChecker::class)
        val certificateRevocationListRepository = mock(CertificateRevocationListRepository::class)
        val epochChangesObserver = mock(EpochChangesObserver::class)

        val epochsFlow = MutableSharedFlow<GroupID>()

        val proposalTimersFlow = MutableSharedFlow<ProposalTimer>()

        suspend fun arrange() = this to MLSConversationDataSource(
            TestUser.SELF.id,
            keyPackageRepository,
            conversationDAO,
            clientApi,
            mlsPublicKeysRepository,
            proposalTimersFlow,
            keyPackageLimitsProvider,
            checkRevocationList,
            certificateRevocationListRepository,
            mutex = Mutex()
        )

        suspend fun withClearProposalTimerSuccessful() = apply {
            coEvery { conversationDAO.clearProposalTimer(any()) }
                .returns(Unit)
        }

        suspend fun withClaimKeyPackagesSuccessful(
            keyPackages: List<KeyPackageDTO> = listOf(KEY_PACKAGE),
            usersWithoutKeyPackages: Set<UserId> = setOf()
        ) = apply {
            coEvery {
                keyPackageRepository.claimKeyPackages(any(), any())
            }.returns(Either.Right(KeyPackageClaimResult(keyPackages, usersWithoutKeyPackages)))
        }

        fun withKeyPackageLimits(refillAmount: Int) = apply {
            every {
                keyPackageLimitsProvider.refillAmount()
            }.returns(refillAmount)
        }

        suspend fun withReplaceKeyPackagesReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                keyPackageRepository.replaceKeyPackages(any(), any(), any())
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

        fun withGetDefaultCipherSuiteSuccessful() = apply {
            every {
                mlsContext.getDefaultCipherSuite()
            }.returns(CIPHER_SUITE.toCrypto())
        }

        suspend fun withGetExternalSenderKeySuccessful() = apply {
            coEvery {
                mlsContext.getExternalSenders(any())
            }.returns(EXTERNAL_SENDER_KEY)
        }

        suspend fun withRotateGroupsSuccessful() = apply {
            coEvery {
                mlsContext.e2eiRotateGroups(any())
            }.returns(Unit)
        }

        suspend fun withRotateGroupsThrowing(exception: Exception, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            coEvery { mlsContext.e2eiRotateGroups(any()) }
                .invokes { _ ->
                    if (invocationCounter < times) {
                        invocationCounter++
                        throw exception
                    } else {
                        Unit
                    }
                }
        }

        suspend fun withSaveX509CredentialsSuccessful(distributionPoints: List<String>?) = apply {
            coEvery {
                mlsContext.saveX509Credential(any(), any())
            }.returns(distributionPoints)
        }

        suspend fun removeStaleKeyPackages() = apply {
            coEvery {
                mlsContext.removeStaleKeyPackages()
            }.returns(Unit)
        }

        suspend fun withGenerateKeyPackageSuccessful(keyPackages: List<ByteArray>) = apply {
            coEvery {
                mlsContext.generateKeyPackages(any())
            }.returns(keyPackages)
        }

        suspend fun withGetDeviceIdentitiesReturn(identities: List<WireIdentity>) = apply {
            coEvery {
                mlsContext.getDeviceIdentities(any(), any())
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

        suspend fun withAddMLSMemberSuccessful(crlNewDistributionPoints: List<String>? = null) = apply {
            coEvery {
                mlsContext.addMember(any(), any())
            }.returns(crlNewDistributionPoints)
        }

        suspend fun withAddMLSMemberThrowing(
            exception: Exception, times: Int = Int.MAX_VALUE,
            crlNewDistributionPoints: List<String>? = null
        ) = apply {
            var invocationCounter = 0
            coEvery { mlsContext.addMember(any(), any()) }
                .invokes { _ ->
                    if (invocationCounter < times) {
                        invocationCounter++
                        throw exception
                    } else {
                        crlNewDistributionPoints
                    }
                }
        }

        suspend fun withGetGroupEpochReturn(epoch: ULong) = apply {
            coEvery {
                mlsContext.conversationEpoch(any())
            }.returns(epoch)
        }


        suspend fun withJoinByExternalCommitSuccessful(welcomeBundle: WelcomeBundle = WELCOME_BUNDLE) = apply {
            coEvery {
                mlsContext.joinByExternalCommit(any())
            }.returns(welcomeBundle)
        }

        suspend fun withJoinByExternalCommitThrowing(
            exception: Exception, times: Int = Int.MAX_VALUE,
            welcomeBundle: WelcomeBundle = WELCOME_BUNDLE,
        ) = apply {
            var invocationCounter = 0
            coEvery { mlsContext.joinByExternalCommit(any()) }
                .invokes { _ ->
                    if (invocationCounter < times) {
                        invocationCounter++
                        throw exception
                    } else {
                        welcomeBundle
                    }
                }
        }

        suspend fun withCommitPendingProposalsSuccessful() = apply {
            coEvery {
                mlsContext.commitPendingProposals(any())
            }.returns(Unit)
        }

        suspend fun withCommitPendingProposalsThrowing(exception: Exception, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            coEvery { mlsContext.commitPendingProposals(any()) }
                .invokes { _ ->
                    if (invocationCounter < times) {
                        invocationCounter++
                        throw exception
                    } else {
                        Unit
                    }
                }
        }

        suspend fun withCommitPendingProposalsReturningNothing(times: Int = Int.MAX_VALUE) = apply {
            withCommitPendingProposalsSuccessful()
            var invocationCounter = 0
            coEvery {
                mlsContext.commitPendingProposals(matches { invocationCounter += 1; invocationCounter <= times })
            }.returns(Unit)
        }

        suspend fun withUpdateKeyingMaterialSuccessful() = apply {
            coEvery {
                mlsContext.updateKeyingMaterial(any())
            }.returns(Unit)
        }

        suspend fun withUpdateKeyingMaterialThrowing(exception: Exception, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            coEvery { mlsContext.updateKeyingMaterial(any()) }
                .invokes { _ ->
                    if (invocationCounter < times) {
                        invocationCounter++
                        throw exception
                    } else {
                        Unit
                    }
                }
        }

        suspend fun withCheckRevocationListResult() = apply {
            coEvery {
                checkRevocationList.check(any(), any())
            }.returns(Either.Right(1uL))
        }

        suspend fun withRemoveStaleKeyPackages() = apply {
            coEvery {
                mlsContext.removeStaleKeyPackages()
            }.returns(Unit)
        }

        suspend fun withDecryptMLSMessageSuccessful(decryptedMessage: com.wire.kalium.cryptography.DecryptedMessageBundle) = apply {
            coEvery {
                mlsContext.decryptMessage(any(), any())
            }.returns(listOf(decryptedMessage))
        }

        suspend fun withRemoveMemberSuccessful() = apply {
            coEvery {
                mlsContext.removeMember(any(), any())
            }.returns(Unit)
        }

        suspend fun withRemoveMemberThrowing(exception: Exception, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            coEvery { mlsContext.removeMember(any(), any()) }
                .invokes { _ ->
                    if (invocationCounter < times) {
                        invocationCounter++
                        throw exception
                    } else {
                        Unit
                    }
                }
        }

        suspend fun withFetchClientsOfUsersSuccessful() = apply {
            coEvery {
                clientApi.listClientsOfUsers(any())
            }.returns(NetworkResponse.Success(value = CLIENTS_OF_USERS_RESPONSE, headers = mapOf(), httpCode = 200))
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
                mlsContext.getUserIdentities(any(), any())
            }.returns(identitiesMap)
        }

        fun withGetDefaultCipherSuite(cipherSuite: CipherSuite) = apply {
            every {
                mlsContext.getDefaultCipherSuite()
            }.returns(cipherSuite.toCrypto())
        }

        suspend fun withMembers(members: List<CryptoQualifiedClientId>) = apply {
            coEvery {
                mlsContext.members(any())
            }.returns(members)
        }

        suspend fun withUpdateMLSGroupIdAndStateSuccessful() = apply {
            coEvery {
                conversationDAO.updateMLSGroupIdAndState(any(), any(), any())
            }.returns(Unit)
        }

        suspend fun withUpdateMLSGroupIdAndStateFailing(failure: StorageFailure.Generic) = apply {
            coEvery {
                conversationDAO.updateMLSGroupIdAndState(any(), any(), any())
            }.throws(failure.rootCause)
        }

        companion object {
            val CIPHER_SUITE = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            val TEST_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
            const val EPOCH = 5UL
            const val RAW_GROUP_ID = "groupId"
            val GROUP_ID = GroupID(RAW_GROUP_ID)
            const val MLS_GROUP_ID = RAW_GROUP_ID
            val TIME = Instant.DISTANT_PAST
            val INVALID_REQUEST_ERROR = Exception("invalid-request")
            val MLS_STALE_MESSAGE_ERROR = Exception("mls-stale-message")
            val MLS_CLIENT_MISMATCH_ERROR = Exception("mls-client-mismatch")
            val MLS_COMMIT_MISSING_REFERENCES_ERROR = Exception("mls-commit-missing-references")
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
            val WELCOME_BUNDLE = WelcomeBundle(MLS_GROUP_ID, null)
            val ROTATE_BUNDLE = RotateBundle(emptyList(), emptyList())
            val CRYPTO_CLIENT_ID = CryptoQualifiedClientId("clientId", TestConversation.USER_1.toCrypto())
            val WIRE_IDENTITY =
                WireIdentity(
                    CRYPTO_CLIENT_ID,
                    CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint",
                    CredentialType.X509,
                    x509Identity = WireIdentity.X509Identity(
                        WireIdentity.Handle(
                            "wireapp",
                            "user_handle",
                            "wire.com"
                        ),
                        "User Test",
                        "domain.com",
                        "certificate",
                        serialNumber = "serialNumber",
                        notAfter = 1899105093,
                        notBefore = 1899205093
                    )
                )
            val E2EI_CONVERSATION_CLIENT_INFO_ENTITY =
                E2EIConversationClientInfoEntity(UserIDEntity(Uuid.random().toString(), "domain.com"), "clientId", "groupId")
            val DECRYPTED_MESSAGE_BUNDLE = com.wire.kalium.cryptography.DecryptedMessageBundle(
                message = null,
                commitDelay = null,
                senderClientId = null,
                hasEpochChanged = true,
                identity = null,
                crlNewDistributionPoints = null
            )
            val MEMBER_LEAVE_EVENT = EventContentDTO.Conversation.MemberLeaveDTO(
                TestConversation.NETWORK_ID,
                TestConversation.NETWORK_USER_ID1,
                Instant.UNIX_FIRST_DATE,
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

    @Test
    fun givenValidConversationAndGroupId_whenUpdateGroupIdAndState_thenShouldSucceed() = runTest {
        val conversationId = TestConversation.ID
        val newGroupId = GroupID("new_group_id")
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateMLSGroupIdAndStateSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateGroupIdAndState(conversationId, newGroupId)

        result.shouldSucceed()
        coVerify {
            arrangement.conversationDAO.updateMLSGroupIdAndState(
                conversationId.toDao(),
                newGroupId.toCrypto(),
                ConversationEntity.GroupState.PENDING_JOIN
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidConversationAndGroupIdWithCustomState_whenUpdateGroupIdAndState_thenShouldSucceedWithCustomState() = runTest {
        val conversationId = TestConversation.ID
        val newGroupId = GroupID("new_group_id")
        val customState = ConversationEntity.GroupState.ESTABLISHED
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateMLSGroupIdAndStateSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateGroupIdAndState(conversationId, newGroupId, customState)

        result.shouldSucceed()
        coVerify {
            arrangement.conversationDAO.updateMLSGroupIdAndState(
                conversationId.toDao(),
                newGroupId.toCrypto(),
                customState
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenDAOFailure_whenUpdateGroupIdAndState_thenShouldPropagateError() = runTest {
        val conversationId = TestConversation.ID
        val newGroupId = GroupID("new_group_id")
        val storageFailure = StorageFailure.Generic(Exception("database error"))
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withUpdateMLSGroupIdAndStateFailing(storageFailure)
            .arrange()

        val result = mlsConversationRepository.updateGroupIdAndState(conversationId, newGroupId)

        result.shouldFail {
            assertIs<StorageFailure.Generic>(it)
        }
    }

    @Test
    fun givenDifferentGroupStates_whenUpdateGroupIdAndState_thenShouldUpdateCorrectly() = runTest {
        val conversationId = TestConversation.ID
        val newGroupId = GroupID("new_group_id")
        val states = listOf(
            ConversationEntity.GroupState.PENDING_CREATION,
            ConversationEntity.GroupState.PENDING_JOIN,
            ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
            ConversationEntity.GroupState.ESTABLISHED
        )

        states.forEach { state ->
            val (arrangement, mlsConversationRepository) = Arrangement()
                .withUpdateMLSGroupIdAndStateSuccessful()
                .arrange()

            val result = mlsConversationRepository.updateGroupIdAndState(conversationId, newGroupId, state)

            result.shouldSucceed()
            coVerify {
                arrangement.conversationDAO.updateMLSGroupIdAndState(
                    conversationId.toDao(),
                    newGroupId.toCrypto(),
                    state
                )
            }.wasInvoked(exactly = once)
        }
    }
}
