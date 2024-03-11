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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JoinExistingMLSConversationUseCaseTest {

    @Test
    fun givenMLSSupportIsDisabled_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() =
        runTest {
            val (arrangement, joinExistingMLSConversationUseCase) = Arrangement()
                .withIsMLSSupported(false)
                .withGetConversationsByIdSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            joinExistingMLSConversationUseCase(Arrangement.MLS_CONVERSATION1.id).shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
                .with(eq(Arrangement.MLS_CONVERSATION1), anything())
                .wasNotInvoked()
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withGetConversationsByIdSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            joinExistingMLSConversationsUseCase(Arrangement.MLS_CONVERSATION1.id).shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
                .with(eq(Arrangement.MLS_CONVERSATION1), anything())
                .wasNotInvoked()
        }

    @Test
    fun givenGroupConversationWithNonZeroEpoch_whenInvokingUseCase_ThenJoinViaExternalCommit() = runTest {
        val conversation = Arrangement.MLS_CONVERSATION1
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful(conversation)
            .withFetchingGroupInfoSuccessful()
            .withJoinByExternalCommitSuccessful()
            .arrange()

        joinExistingMLSConversationsUseCase(conversation.id).shouldSucceed()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
            .with(eq((conversation.protocol as Conversation.ProtocolInfo.MLS).groupId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenGroupConversationWithZeroEpoch_whenInvokingUseCase_ThenDoNotEstablishMlsGroup() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_GROUP_CONVERSATION)
                .arrange()

            joinExistingMLSConversationsUseCase(Arrangement.MLS_UNESTABLISHED_GROUP_CONVERSATION.id).shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
                .with(eq(Arrangement.GROUP_ID3), eq(emptyList()))
                .wasNotInvoked()
        }

    @Test
    fun givenSelfConversationWithZeroEpoch_whenInvokingUseCase_ThenEstablishMlsGroup() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_SELF_CONVERSATION)
                .withEstablishMLSGroupSuccessful(MLSAdditionResult(emptySet(), emptySet()))
                .arrange()

            joinExistingMLSConversationsUseCase(Arrangement.MLS_UNESTABLISHED_SELF_CONVERSATION.id).shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
                .with(eq(Arrangement.GROUP_ID_SELF), eq(emptyList()))
                .wasInvoked(once)
        }

    @Test
    fun givenOneOnOneConversationWithZeroEpoch_whenInvokingUseCase_ThenEstablishMlsGroup() =
        runTest {
            val members = listOf(TestUser.USER_ID, TestUser.OTHER_USER_ID)
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByIdSuccessful(Arrangement.MLS_UNESTABLISHED_ONE_ONE_ONE_CONVERSATION)
                .withGetConversationMembersSuccessful(members)
                .withEstablishMLSGroupSuccessful(MLSAdditionResult(emptySet(), emptySet()))
                .arrange()

            joinExistingMLSConversationsUseCase(Arrangement.MLS_UNESTABLISHED_ONE_ONE_ONE_CONVERSATION.id).shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
                .with(eq(Arrangement.GROUP_ID_ONE_ON_ONE), eq(members))
                .wasInvoked(once)
        }

    @Test
    fun givenOutOfDateEpochFailure_whenInvokingUseCase_ThenRetryWithNewEpoch() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful(Arrangement.MLS_CONVERSATION1)
            .withJoinByExternalCommitSuccessful()
            .withJoinByExternalCommitGroupFailing(Arrangement.MLS_STALE_MESSAGE_FAILURE, times = 1)
            .withFetchConversationSuccessful()
            .withFetchingGroupInfoSuccessful()
            .arrange()

        joinExistingMLSConversationsUseCase(Arrangement.MLS_CONVERSATION1.id).shouldSucceed()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(Arrangement.MLS_CONVERSATION1.id))
            .wasInvoked(once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
            .with(eq(Arrangement.GROUP_ID1), anything())
            .wasInvoked(twice)

    }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_ThenFailureIsReported() = runTest {
        val (_, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful()
            .withFetchingGroupInfoSuccessful()
            .withJoinByExternalCommitGroupFailing(Arrangement.MLS_UNSUPPORTED_PROPOSAL_FAILURE)
            .arrange()

        joinExistingMLSConversationsUseCase(Arrangement.MLS_CONVERSATION1.id).shouldFail()
    }

    private class Arrangement {

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val conversationApi = mock(classOf<ConversationApi>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        fun arrange() = this to JoinExistingMLSConversationUseCaseImpl(
            featureSupport,
            conversationApi,
            clientRepository,
            conversationRepository,
            mlsConversationRepository
        )

        @Suppress("MaxLineLength")
        fun withGetConversationsByIdSuccessful(conversation: Conversation = MLS_CONVERSATION1) =
            apply {
                given(conversationRepository)
                    .suspendFunction(conversationRepository::baseInfoById)
                    .whenInvokedWith(anything())
                    .then { Either.Right(conversation) }
            }

        fun withFetchConversationSuccessful() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(anything())
                .then { Either.Right(Unit) }
        }

        fun withGetConversationMembersSuccessful(members: List<UserId>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(anything())
                .then { Either.Right(members) }
        }

        fun withEstablishMLSGroupSuccessful(additionResult: MLSAdditionResult) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(additionResult))
        }

        fun withJoinByExternalCommitSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::joinGroupByExternalCommit)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withJoinByExternalCommitGroupFailing(failure: CoreFailure, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::joinGroupByExternalCommit)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times }, anything())
                .thenReturn(Either.Left(failure))
        }

        fun withFetchingGroupInfoSuccessful() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchGroupInfo)
                .whenInvokedWith(anything())
                .thenReturn(NetworkResponse.Success(PUBLIC_GROUP_STATE, mapOf(), 200))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun withHasRegisteredMLSClient(result: Boolean) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(Either.Right(result))
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
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id1", "domain"))

            val MLS_CONVERSATION2 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID2,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id2", "domain"))

            val MLS_UNESTABLISHED_GROUP_CONVERSATION = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID3,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id3", "domain"))

            val MLS_UNESTABLISHED_SELF_CONVERSATION = TestConversation.SELF(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID_SELF,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("self", "domain"))

            val MLS_UNESTABLISHED_ONE_ONE_ONE_CONVERSATION = TestConversation.ONE_ON_ONE(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID_ONE_ON_ONE,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 0UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("one-on-one", "domain"))
        }
    }
}
