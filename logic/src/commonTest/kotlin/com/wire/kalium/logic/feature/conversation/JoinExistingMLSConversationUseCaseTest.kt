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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
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
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
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

            coVerify {
                arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                    groupID = any(),
                    groupInfo = any(),
                    mlsContext = any()
                )
            }.wasNotInvoked()
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

            coVerify {
                arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                    any(),
                    any(),
                    any()
                )
            }.wasNotInvoked()
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

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                any(), eq((conversation.protocol as Conversation.ProtocolInfo.MLS).groupId), any()
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenGroupConversationWithZeroEpoch_whenInvokingUseCase_ThenDoNotEstablishMlsGroup() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(testKaliumDispatcher)
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_GROUP_CONVERSATION)
                .arrange()

            joinExistingMLSConversationsUseCase(
                arrangement.transactionContext,
                Arrangement.MLS_UNESTABLISHED_GROUP_CONVERSATION.id
            ).shouldSucceed()

            coVerify {
                arrangement.mlsConversationRepository.establishMLSGroup(
                    groupID = Arrangement.GROUP_ID3,
                    members = emptyList(),
                    publicKeys = null,
                    allowSkippingUsersWithoutKeyPackages = false,
                    mlsContext = arrangement.mlsContext
                )
            }.wasNotInvoked()
        }

    @Test
    fun givenSelfConversationWithZeroEpoch_whenInvokingUseCase_ThenEstablishMlsGroup() = runTest {
        // GIVEN
        val (arrangement, joinExistingMLSConversationUseCase) = Arrangement(testKaliumDispatcher)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_SELF_CONVERSATION)
            .withEstablishMLSGroupSuccessful(MLSAdditionResult(emptySet(), emptySet()))
            .arrange()

        // WHEN
        val result = joinExistingMLSConversationUseCase(arrangement.transactionContext, Arrangement.MLS_UNESTABLISHED_SELF_CONVERSATION.id)

        // THEN
        result.shouldSucceed()

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(
                any(),
                eq(Arrangement.GROUP_ID_SELF),
                eq(listOf(arrangement.selfUserId)),
                any(),
                eq(false)
            )
        }.wasInvoked(once)
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
                .withEstablishMLSGroupSuccessful(MLSAdditionResult(emptySet(), emptySet()))
                .arrange()

            joinExistingMLSConversationsUseCase(
                arrangement.transactionContext,
                Arrangement.MLS_UNESTABLISHED_ONE_ONE_ONE_CONVERSATION.id
            ).shouldSucceed()

            coVerify {
                arrangement.mlsConversationRepository.establishMLSGroup(
                    any(),
                    eq(Arrangement.GROUP_ID_ONE_ON_ONE),
                    eq(members),
                    any(),
                    eq(false)
                )
            }.wasInvoked(once)
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

        coVerify {
            arrangement.fetchConversation(any(), eq(Arrangement.MLS_CONVERSATION1.id))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(any(), eq(Arrangement.GROUP_ID1), any())
        }.wasInvoked(twice)

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
        val featureSupport = mock(FeatureSupport::class)
        val conversationApi = mock(ConversationApi::class)
        val clientRepository = mock(ClientRepository::class)
        val conversationRepository = mock(ConversationRepository::class)
        val mlsConversationRepository = mock(MLSConversationRepository::class)
        val fetchMLSOneToOneConversation = mock(FetchMLSOneToOneConversationUseCase::class)
        val fetchConversation = mock(FetchConversationUseCase::class)
        val resetMlsConversation = mock(ResetMLSConversationUseCase::class)

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

            coEvery {
                resetMlsConversation.invoke(any())
            } returns Unit.right()
        }

        @Suppress("MaxLineLength")
        suspend fun withGetConversationsByIdSuccessful(conversation: Conversation = MLS_CONVERSATION1) =
            apply {
                coEvery {
                    conversationRepository.getConversationById(any())
                }.returns(Either.Right(conversation))
            }

        suspend fun withFetchConversationSuccessful() = apply {
            coEvery {
                fetchConversation.invoke(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetConversationMembersSuccessful(members: List<UserId>) = apply {
            coEvery {
                conversationRepository.getConversationMembers(any())
            }.returns(Either.Right(members))
        }

        suspend fun withEstablishMLSGroupSuccessful(additionResult: MLSAdditionResult) = apply {
            coEvery {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            }.returns(Either.Right(additionResult))
        }

        suspend fun withJoinByExternalCommitSuccessful() = apply {
            coEvery {
                mlsConversationRepository.joinGroupByExternalCommit(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withJoinByExternalCommitGroupFailing(failure: CoreFailure, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            coEvery {
                mlsConversationRepository.joinGroupByExternalCommit(
                    any(), matches {
                        invocationCounter += 1
                        invocationCounter <= times
                    }, any()
                )
            }.returns(Either.Left(failure))
        }

        suspend fun withFetchingGroupInfoSuccessful() = apply {
            coEvery {
                conversationApi.fetchGroupInfo(any())
            }.returns(NetworkResponse.Success(PUBLIC_GROUP_STATE, mapOf(), 200))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(supported)
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            coEvery {
                clientRepository.hasRegisteredMLSClient()
            }.returns(Either.Right(result))
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

            val MLS_STALE_MESSAGE_FAILURE = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
                        403,
                        "The conversation epoch in a message is too old",
                        "mls-stale-message"
                    )
                )
            )

            val GROUP_ID1 = GroupID("group1")
            val GROUP_ID2 = GroupID("group2")
            val GROUP_ID3 = GroupID("group3")
            val GROUP_ID_ONE_ON_ONE = GroupID("group-one-on-ne")
            val GROUP_ID_SELF = GroupID("group-self")

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
