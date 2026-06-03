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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class JoinExistingMLSConversationsUseCaseTest {

    @Test
    fun givenMLSSupportIsDisabled_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement().withIsMLSSupported(false)
            .withJoinExistingMLSConversationSuccessful().withGetConversationsByGroupStateSuccessful().arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
    }

    @Test
    fun givenNoMLSClientIsRegistered_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement().withIsMLSSupported(true).withHasRegisteredMLSClient(false)
            .withJoinExistingMLSConversationSuccessful().withGetConversationsByGroupStateSuccessful().arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
    }

    @Test
    fun givenExistingConversations_whenInvokingUseCase_ThenRequestToJoinConversationIsCalledForAllConversations() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement().withIsMLSSupported(true).withHasRegisteredMLSClient(true)
            .withJoinExistingMLSConversationSuccessful().withGetConversationsByGroupStateSuccessful().arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
    }

    @Test
    fun givenNoKeyPackagesAvailable_WhenJoinExistingMLSConversationUseCase_ThenReturnUnit() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement().withIsMLSSupported(true).withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful().withNoKeyPackagesAvailable().arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.pendingActionsRepository.enqueuePendingMLSGroupJoin(any())
        }
    }

    @Test
    fun givenRetryableFederatedFailure_WhenJoinExistingMLSConversationUseCase_ThenEnqueueForegroundRecovery() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful(listOf(Arrangement.MLS_CONVERSATION1))
            .withJoinExistingMLSConversationFailure(
                NetworkFailure.FederatedBackendFailure.FailedDomains(listOf(Arrangement.MLS_CONVERSATION1.id.domain))
            )
            .arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionsRepository.enqueuePendingMLSGroupJoin(eq(Arrangement.MLS_CONVERSATION1.id))
        }
    }

    @Test
    fun givenStaleMessageFailure_WhenJoinExistingMLSConversationUseCase_ThenEnqueueForegroundRecovery() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful(listOf(Arrangement.MLS_CONVERSATION1))
            .withJoinExistingMLSConversationFailure(
                MLSFailure.MessageRejected(NetworkFailure.MlsMessageRejectedFailure.StaleMessage)
            )
            .arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionsRepository.enqueuePendingMLSGroupJoin(eq(Arrangement.MLS_CONVERSATION1.id))
        }
    }

    @Test
    fun givenNetworkFailure_WhenJoinExistingMLSConversationUseCase_ThenPropagateFailure() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement().withIsMLSSupported(true).withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful().withJoinExistingMLSConversationNetworkFailure().arrange()

        joinExistingMLSConversationsUseCase().shouldFail {
            assertIs<NetworkFailure>(it)
        }
        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
    }

    @Test
    fun givenOtherFailure_WhenJoinExistingMLSConversationUseCase_ThenReturnUnit() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement().withIsMLSSupported(true).withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful().withJoinExistingMLSConversationFailure().arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
    }

    @Test
    fun givenServerMiscommunicationWithNonThrottleInvalidRequestError_WhenJoiningMLSConversation_ThenSkipConversation() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful()
            .withJoinExistingMLSConversationReturningInvalidRequestServerMiscommunication()
            .arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(any(), any(), any())
        }
    }

    @Test
    fun givenThrottleInvalidRequestErrorWithoutRetry_WhenJoiningMLSConversation_ThenPropagateFailure() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful(listOf(Arrangement.MLS_CONVERSATION1))
            .withJoinExistingMLSConversationReturningThrottleServerMiscommunication()
            .arrange()

        joinExistingMLSConversationsUseCase(keepRetryingOnFailure = false).shouldFail {
            assertIs<NetworkFailure.ServerMiscommunication>(it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
    }

    @Test
    fun givenThrottleInvalidRequestErrorWithRetry_WhenJoiningMLSConversation_ThenRetryBeforePropagatingFailure() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement(
            maxThrottleRetries = 2,
            throttleRetryDelayMs = 1,
        )
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful(listOf(Arrangement.MLS_CONVERSATION1))
            .withJoinExistingMLSConversationReturningThrottleServerMiscommunication()
            .arrange()

        joinExistingMLSConversationsUseCase().shouldFail {
            assertIs<NetworkFailure.ServerMiscommunication>(it)
        }

        verifySuspend(VerifyMode.exactly(3)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
    }

    @Test
    fun givenExternalCommitJoinDisabled_whenInvokingUseCase_thenForwardFlagToPerConversationJoin() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful(listOf(Arrangement.MLS_CONVERSATION1))
            .withJoinExistingMLSConversationSuccessful()
            .arrange()

        joinExistingMLSConversationsUseCase(allowJoinByExternalCommit = false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), eq(false))
        }
    }

    private class Arrangement(
        private val maxConcurrentJoins: Int = 4,
        private val maxThrottleRetries: Int = 3,
        private val throttleRetryDelayMs: Long = 250L,
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val featureSupport = mock<FeatureSupport>(mode = MockMode.autoUnit)
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val joinExistingMLSConversationUseCase = mock<JoinExistingMLSConversationUseCase>(mode = MockMode.autoUnit)
        val pendingActionsRepository = mock<PendingActionsRepository>(mode = MockMode.autoUnit)

        suspend fun arrange() = this to JoinExistingMLSConversationsUseCaseImpl(
            featureSupport = featureSupport,
            clientRepository = clientRepository,
            conversationRepository = conversationRepository,
            joinExistingMLSConversationUseCase = joinExistingMLSConversationUseCase,
            transactionProvider = cryptoTransactionProvider,
            pendingActionsRepository = pendingActionsRepository,
            maxConcurrentJoins = maxConcurrentJoins,
            maxThrottleRetries = maxThrottleRetries,
            throttleRetryDelayMs = throttleRetryDelayMs,
        ).also {
            withTransactionReturning(Either.Right(Unit))
            everySuspend { pendingActionsRepository.enqueuePendingMLSGroupJoin(any()) } returns Unit
        }

        @Suppress("MaxLineLength")
        suspend fun withGetConversationsByGroupStateSuccessful(
            conversations: List<Conversation> = listOf(MLS_CONVERSATION1, MLS_CONVERSATION2)
        ) = apply {
            everySuspend {
                conversationRepository.getConversationsByGroupState(any())
            } returns Either.Right(conversations)
        }

        suspend fun withJoinExistingMLSConversationSuccessful() = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withJoinExistingMLSConversationNetworkFailure() = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        suspend fun withJoinExistingMLSConversationFailure(
            failure: CoreFailure = CoreFailure.NotSupportedByProteus
        ) = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns Either.Left(failure)
        }

        suspend fun withNoKeyPackagesAvailable() = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns Either.Left(CoreFailure.MissingKeyPackages(setOf()))
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

        suspend fun withJoinExistingMLSConversationReturningInvalidRequestServerMiscommunication() = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns(
                Either.Left(
                    NetworkFailure.ServerMiscommunication(
                        KaliumException.InvalidRequestError(
                            errorResponse = GenericAPIErrorResponse(400, "Invalid LeafNode signature", "mls-protocol-error")
                        )
                    )
                )
            )
        }

        suspend fun withJoinExistingMLSConversationReturningThrottleServerMiscommunication() = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns(
                Either.Left(
                    NetworkFailure.ServerMiscommunication(
                        KaliumException.InvalidRequestError(
                            errorResponse = GenericAPIErrorResponse(420, "unknown status code", "throttled by ingress")
                        )
                    )
                )
            )
        }

        companion object {
            val GROUP_ID1 = GroupID("group1")
            val GROUP_ID2 = GroupID("group2")

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
        }
    }
}
