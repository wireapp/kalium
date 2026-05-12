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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.sequentiallyReturns
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JoinExistingMLSConversationUseCaseTest {

    @Test
    fun givenMLSSupportIsDisabled_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() =
        runTest {
            val (arrangement, joinExistingMLSConversationUseCase) = Arrangement(testKaliumDispatcher)
                .withIsMLSSupported(false)
                .withGetConversationsByIdSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            joinExistingMLSConversationUseCase(arrangement.transactionContext, Arrangement.MLS_CONVERSATION1.id).shouldSucceed()

            verifySuspend(VerifyMode.not) {
                arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                    groupID = any(),
                    groupInfo = any(),
                    mlsContext = any()
                )
            }
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withGetConversationsByIdSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            joinExistingMLSConversationsUseCase(arrangement.transactionContext, Arrangement.MLS_CONVERSATION1.id).shouldSucceed()

            verifySuspend(VerifyMode.not) {
                arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun givenGroupConversationWithNonZeroEpoch_whenInvokingUseCase_ThenJoinViaExternalCommit() = runTest {
        val conversation = Arrangement.MLS_CONVERSATION1
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful(conversation)
            .withFetchingGroupInfoSuccessful()
            .withJoinByExternalCommitSuccessful()
            .arrange()

        joinExistingMLSConversationsUseCase(arrangement.transactionContext, conversation.id).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                any(), eq((conversation.protocol as Conversation.ProtocolInfo.MLS).groupId), any()
            )
        }
    }

    @Test
    fun givenConversationIsDeletedLocally_whenInvokingUseCase_thenDoNotJoinConversation() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetNonDeletedConversationByIdFailure(StorageFailure.DataNotFound)
            .arrange()

        joinExistingMLSConversationsUseCase(arrangement.transactionContext, Arrangement.MLS_CONVERSATION1.id).shouldFail()

        verifySuspend(VerifyMode.not) {
            arrangement.conversationApi.fetchGroupInfo(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenGroupConversationWithZeroEpoch_whenInvokingUseCase_ThenEstablishMlsGroup() =
        runTest {
            val members = listOf(TestUser.USER_ID, TestUser.OTHER_USER_ID)
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_GROUP_CONVERSATION)
                .withGetConversationMembersSuccessful(members)
                .withEstablishMLSGroupSuccessful(MLSAdditionResult(emptySet(), emptySet(), emptySet()))
                .arrange()

            joinExistingMLSConversationsUseCase(
                arrangement.transactionContext,
                Arrangement.MLS_UNESTABLISHED_GROUP_CONVERSATION.id
            ).shouldSucceed()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.establishMLSGroup(
                    mlsContext = any(),
                    groupID = eq(Arrangement.GROUP_ID3),
                    members = eq(members),
                    publicKeys = any(),
                    allowSkippingUsersWithoutKeyPackages = eq(false)
                )
            }
        }

    @Test
    fun givenSelfConversationWithZeroEpoch_whenInvokingUseCase_ThenEstablishMlsGroup() = runTest {
        val (arrangement, joinExistingMLSConversationUseCase) = Arrangement(testKaliumDispatcher)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_SELF_CONVERSATION)
            .withEstablishMLSGroupSuccessful(MLSAdditionResult(emptySet(), emptySet(), emptySet()))
            .arrange()

        val result = joinExistingMLSConversationUseCase(arrangement.transactionContext, Arrangement.MLS_UNESTABLISHED_SELF_CONVERSATION.id)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(
                any(),
                eq(Arrangement.GROUP_ID_SELF),
                eq(listOf(arrangement.selfUserId)),
                any(),
                eq(false)
            )
        }
    }

    @Test
    fun givenOneOnOneConversationWithZeroEpoch_whenInvokingUseCase_ThenEstablishMlsGroup() =
        runTest {
            val members = listOf(TestUser.USER_ID, TestUser.OTHER_USER_ID)
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_ONE_ONE_ONE_CONVERSATION)
                .withGetConversationMembersSuccessful(members)
                .withEstablishMLSGroupSuccessful(MLSAdditionResult(emptySet(), emptySet(), emptySet()))
                .arrange()

            joinExistingMLSConversationsUseCase(
                arrangement.transactionContext,
                Arrangement.MLS_UNESTABLISHED_ONE_ONE_ONE_CONVERSATION.id
            ).shouldSucceed()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.establishMLSGroup(
                    mlsContext = any(),
                    groupID = eq(Arrangement.GROUP_ID_ONE_ON_ONE),
                    members = eq(members),
                    publicKeys = any(),
                    allowSkippingUsersWithoutKeyPackages = eq(false)
                )
            }
        }

    @Test
    fun givenOutOfDateEpochFailure_whenInvokingUseCase_ThenRetryWithNewEpoch() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful(Arrangement.MLS_CONVERSATION1)
            .withJoinByExternalCommitSuccessful()
            .withJoinByExternalCommitGroupFailing(Arrangement.MLS_STALE_MESSAGE_FAILURE, times = 1)
            .withFetchConversationSuccessful()
            .withFetchingGroupInfoSuccessful()
            .arrange()

        joinExistingMLSConversationsUseCase(arrangement.transactionContext, Arrangement.MLS_CONVERSATION1.id).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversation(
                any(),
                eq(Arrangement.MLS_CONVERSATION1.id),
                eq(ConversationSyncReason.Other)
            )
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(any(), eq(Arrangement.GROUP_ID1), any())
        }

    }

    @Test
    fun givenPendingAfterResetConversation_whenInvokingUseCase_thenRefreshMetadataBeforeChoosingRejoinStrategy() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSequentially(
                Arrangement.MLS_PENDING_AFTER_RESET_GROUP_CONVERSATION,
                Arrangement.MLS_RESET_REJOINED_GROUP_CONVERSATION
            )
            .withFetchConversationSuccessful()
            .withFetchingGroupInfoSuccessful()
            .withJoinByExternalCommitSuccessful()
            .arrange()

        joinExistingMLSConversationsUseCase(
            arrangement.transactionContext,
            Arrangement.MLS_PENDING_AFTER_RESET_GROUP_CONVERSATION.id
        ).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversation(
                any(),
                eq(Arrangement.MLS_PENDING_AFTER_RESET_GROUP_CONVERSATION.id),
                eq(ConversationSyncReason.Other)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                any(),
                eq(Arrangement.GROUP_ID3),
                any()
            )
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_ThenFailureIsReported() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful()
            .withFetchingGroupInfoSuccessful()
            .withJoinByExternalCommitGroupFailing(Arrangement.MLS_UNSUPPORTED_PROPOSAL_FAILURE)
            .arrange()

        joinExistingMLSConversationsUseCase(arrangement.transactionContext, Arrangement.MLS_CONVERSATION1.id).shouldFail()
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val featureSupport = mock<FeatureSupport>(mode = MockMode.autoUnit)
        val conversationApi = mock<ConversationApi>(mode = MockMode.autoUnit)
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val fetchMLSOneToOneConversation = mock<FetchMLSOneToOneConversationUseCase>(mode = MockMode.autoUnit)
        val fetchConversation = mock<FetchConversationUseCase>(mode = MockMode.autoUnit)
        val resetMlsConversation = mock<ResetMLSConversationUseCase>(mode = MockMode.autoUnit)
        private var localGroupExists: Boolean = false
        private var localGroupEpoch: ULong = LOCAL_GROUP_EPOCH

        val selfUserId = TestUser.USER_ID

        suspend fun arrange() = this to JoinExistingMLSConversationUseCaseImpl(
            featureSupport,
            conversationApi,
            clientRepository,
            conversationRepository,
            mlsConversationRepository,
            fetchMLSOneToOneConversation,
            fetchConversation,
            resetMlsConversation,
            selfUserId,
            dispatcher
        ).also {
            withTransactionReturning(Either.Right(Unit))
            everySuspend {
                mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
            } calls {
                Either.Right(localGroupExists)
            }
            everySuspend {
                mlsConversationRepository.getLocalGroupEpoch(any(), any())
            } returns Either.Right(localGroupEpoch)
            everySuspend {
                conversationRepository.updateConversationGroupState(any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                mlsConversationRepository.updateGroupIdAndState(any(), any(), any(), any())
            } returns Either.Right(Unit)

            everySuspend {
                resetMlsConversation.invoke(any())
            } returns ResetMLSConversationResult.Success
        }

        @Suppress("MaxLineLength")
        suspend fun withGetConversationsByIdSuccessful(conversation: Conversation = MLS_CONVERSATION1) =
            apply {
                everySuspend {
                    conversationRepository.getNonDeletedConversationById(any())
                } returns Either.Right(conversation)
            }

        suspend fun withGetConversationsByIdSequentially(vararg conversations: Conversation) = apply {
            everySuspend {
                conversationRepository.getNonDeletedConversationById(any())
            } sequentiallyReturns conversations.map { Either.Right(it) }
        }

        suspend fun withGetNonDeletedConversationByIdFailure(failure: StorageFailure) = apply {
            everySuspend {
                conversationRepository.getNonDeletedConversationById(any())
            } returns Either.Left(failure)
        }

        suspend fun withFetchConversationSuccessful() = apply {
            everySuspend {
                fetchConversation.invoke(any(), any(), eq(ConversationSyncReason.Other))
            } returns Either.Right(Unit)
        }

        suspend fun withGetConversationMembersSuccessful(members: List<UserId>) = apply {
            everySuspend {
                conversationRepository.getConversationMembers(any())
            } returns Either.Right(members)
        }

        suspend fun withEstablishMLSGroupSuccessful(additionResult: MLSAdditionResult) = apply {
            everySuspend {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            } returns Either.Right(additionResult)
        }

        suspend fun withJoinByExternalCommitSuccessful() = apply {
            everySuspend {
                mlsConversationRepository.joinGroupByExternalCommit(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withLocalGroupExists(exists: Boolean) = apply {
            localGroupExists = exists
        }

        suspend fun withLocalGroupEpoch(epoch: ULong) = apply {
            localGroupEpoch = epoch
        }

        suspend fun withJoinByExternalCommitGroupFailing(failure: CoreFailure, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            everySuspend {
                mlsConversationRepository.joinGroupByExternalCommit(
                    any(), matching {
                        invocationCounter += 1
                        invocationCounter <= times
                    }, any()
                )
            } returns Either.Left(failure)
        }

        suspend fun withFetchingGroupInfoSuccessful() = apply {
            everySuspend {
                conversationApi.fetchGroupInfo(any())
            } returns NetworkResponse.Success(PUBLIC_GROUP_STATE, mapOf(), 200)
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            } returns supported
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            everySuspend {
                clientRepository.hasRegisteredMLSClient()
            } returns Either.Right(result)
        }

        companion object {
            val PUBLIC_GROUP_STATE = "public_group_state".encodeToByteArray()

            val MLS_UNSUPPORTED_PROPOSAL_FAILURE = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
                        422,
                        "Unsupported proposal type",
                        "mls-unsupported-proposal"
                    )
                )
            )

            val MLS_STALE_MESSAGE_FAILURE = NetworkFailure.MlsMessageRejectedFailure.StaleMessage

            val GROUP_ID1 = GroupID("group1")
            val GROUP_ID2 = GroupID("group2")
            val GROUP_ID3 = GroupID("group3")
            val GROUP_ID_ONE_ON_ONE = GroupID("group-one-on-ne")
            val GROUP_ID_SELF = GroupID("group-self")
            val LOCAL_GROUP_EPOCH = 7UL

            val MLS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID1,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id1", "domain"))

            val MLS_CONVERSATION2 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID2,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id2", "domain"))

            val MLS_UNESTABLISHED_GROUP_CONVERSATION = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID3,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id3", "domain"))

            val MLS_PENDING_AFTER_RESET_GROUP_CONVERSATION = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID3,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id-pending-after-reset", "domain"))

            val MLS_RESET_REJOINED_GROUP_CONVERSATION = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID3,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET,
                    epoch = 2UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id-pending-after-reset", "domain"))

            val MLS_ESTABLISHED_GROUP_CONVERSATION = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID3,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id4", "domain"))

            val MLS_UNESTABLISHED_SELF_CONVERSATION = TestConversation.SELF(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID_SELF,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("self", "domain"))

            val MLS_UNESTABLISHED_ONE_ONE_ONE_CONVERSATION = TestConversation.ONE_ON_ONE(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID_ONE_ON_ONE,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("one-on-one", "domain"))
        }
    }
}
