/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.mokkery.answering.calls
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.ConversationPersistenceApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ResetMLSConversationUseCaseTest {

    private val TEST_CONVERSATION_ID = UserId("testConversation", "domain")

    @Test
    fun givenCompileTimeFlagDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagDisabled()
            .withRuntimeFlagEnabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    @Test
    fun givenCompileTimeFlagEnabledAndRuntimeFlagDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagDisabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    @Test
    fun givenBothFlagsEnabled_whenUseCaseCalled_thenResetConversationStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagEnabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    @Test
    fun givenFeatureDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureDisabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    @Test
    fun givenRepositorySuccess_whenUseCaseCalled_thenResetMLSConversationCalled() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    @OptIn(ConversationPersistenceApi::class)
    @Test
    fun givenResetReturnsMlsStaleMessage_whenUseCaseCalled_thenConversationIsRefetchedAndResetRetriedWithRemoteEpoch() = runTest {
        val remoteEpoch = 42UL

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withResetMlsConversationResponses(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(409, "epoch is too old", "mls-stale-message")
                    )
                ).left(),
                Unit.right()
            )
            .withRemoteConversationResponse(
                TestConversation.CONVERSATION_RESPONSE.copy(
                    protocol = ConvProtocol.MLS,
                    groupId = TestConversation.GROUP_ID.value,
                    epoch = remoteEpoch
                )
            )
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(15UL))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.fetchConversation(eq(TEST_CONVERSATION_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(remoteEpoch))
        }
    }

    @OptIn(ConversationPersistenceApi::class)
    @Test
    fun givenResetKeepsReturningMlsStaleMessageAndRemoteGroupIdChanges_whenUseCaseCalled_thenRetryIsCancelledAndConversationIsSynced() = runTest {
        val refreshedEpoch = 42UL
        val updatedGroupId = "remote-group-id"

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withResetMlsConversationResponses(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(409, "epoch is too old", "mls-stale-message")
                    )
                ).left(),
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(409, "epoch is too old", "mls-stale-message")
                    )
                ).left()
            )
            .withRemoteConversationResponses(
                TestConversation.CONVERSATION_RESPONSE.copy(
                    protocol = ConvProtocol.MLS,
                    groupId = TestConversation.GROUP_ID.value,
                    epoch = refreshedEpoch
                ),
                TestConversation.CONVERSATION_RESPONSE.copy(
                    protocol = ConvProtocol.MLS,
                    groupId = updatedGroupId,
                    epoch = refreshedEpoch
                )
            )
            .withConversations(
                TestConversation.MLS_CONVERSATION,
                TestConversation.MLS_CONVERSATION.copy(
                    protocol = Conversation.ProtocolInfo.MLS(
                        groupId = GroupID(updatedGroupId),
                        groupState = TestConversation.MLS_PROTOCOL_INFO.groupState,
                        epoch = refreshedEpoch,
                        keyingMaterialLastUpdate = TestConversation.MLS_PROTOCOL_INFO.keyingMaterialLastUpdate,
                        cipherSuite = TestConversation.MLS_PROTOCOL_INFO.cipherSuite
                    )
                )
            )
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(15UL))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(refreshedEpoch))
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.conversationRepository.fetchConversation(eq(TEST_CONVERSATION_ID))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.resetMlsConversation(eq(GroupID(updatedGroupId)), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversationUseCase(
                transactionContext = any(),
                conversationId = eq(TEST_CONVERSATION_ID),
                reason = eq(ConversationSyncReason.ConversationReset)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), eq(GroupID(updatedGroupId)), any(), any(), any())
        }
    }

    @Test
    fun whenUseCaseSuccess_thenLeaveConversationCalled() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }
    }

    @Test
    fun whenUseCaseInvoked_thenConversationFetchedAfterReset() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversationUseCase(
                conversationId = any(),
                transactionContext = any(),
                reason = eq(ConversationSyncReason.ConversationReset)
            )
        }
    }

    @Test
    fun whenUseCaseInvoked_thenConversationEstablishedAfterReset() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenMLSProtocol_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withConversation(TestConversation.MLS_CONVERSATION)
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    @Test
    fun givenMixedProtocol_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withConversation(TestConversation.MIXED_CONVERSATION)
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    @Test
    fun givenLeaveGroupFails_whenUseCaseCalled_thenStillSucceeds() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withLeaveGroupFailing()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenLeaveGroupSucceeds_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenFederatedConversation_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagDisabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID.copy(domain = "domainFederated")).toEither()

        assertTrue(result.isRight())

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        private val TEST_USER_ID = UserId("testUser", "domain")

        val userConfig: UserConfigRepository = mock(mode = MockMode.autoUnit)
        val conversationRepository: ConversationRepository = mock(mode = MockMode.autoUnit)
        val mlsConversationRepository: MLSConversationRepository = mock(mode = MockMode.autoUnit)
        val fetchConversationUseCase: FetchConversationUseCase = mock(mode = MockMode.autoUnit)
        var kaliumConfigs = KaliumConfigs(isMlsResetEnabled = true)
        private var remoteConversationResponses: List<ConversationResponse> = listOf(TestConversation.CONVERSATION_RESPONSE.copy(
            protocol = ConvProtocol.MLS,
            groupId = TestConversation.GROUP_ID.value,
            epoch = 21UL
        ))
        private var resetConversationResults: List<Either<NetworkFailure, Unit>> = listOf(Unit.right())
        private var conversations: List<Conversation> = listOf(TestConversation.MLS_CONVERSATION)

        fun withCompileTimeFlagDisabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = false)
        }

        fun withCompileTimeFlagEnabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = true)
        }

        fun withRuntimeFlagDisabled() = apply {
            everySuspend { userConfig.isMlsConversationsResetEnabled() } returns false
        }

        fun withRuntimeFlagEnabled() = apply {
            everySuspend { userConfig.isMlsConversationsResetEnabled() } returns true
        }

        fun withFeatureDisabled() = apply {
            withCompileTimeFlagEnabled()
            withRuntimeFlagDisabled()
        }

        fun withFeatureEnabled() = apply {
            withCompileTimeFlagEnabled()
            withRuntimeFlagEnabled()
        }

        fun withConversation(conversation: Conversation) = apply {
            everySuspend {
                conversationRepository.getConversationById(any())
            } returns conversation.right()
            this.conversations = listOf(conversation)
        }

        fun withConversations(vararg conversations: Conversation) = apply {
            this.conversations = conversations.toList()
        }

        fun withRemoteConversationResponse(conversationResponse: ConversationResponse) = apply {
            remoteConversationResponses = listOf(conversationResponse)
        }

        fun withRemoteConversationResponses(vararg conversationResponses: ConversationResponse) = apply {
            remoteConversationResponses = conversationResponses.toList()
        }

        fun withResetMlsConversationResponses(vararg results: Either<NetworkFailure, Unit>) = apply {
            resetConversationResults = results.toList()
        }

        fun withLeaveGroupFailing() = apply {
            everySuspend {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns CoreFailure.Unknown(RuntimeException("Leave group failed")).left()
        }

        @OptIn(ConversationPersistenceApi::class)
        suspend fun arrange(): Pair<Arrangement, ResetMLSConversationUseCaseImpl> {
            val conversationResults = conversations.map { it.right() }
            val remoteConversationResults = remoteConversationResponses.map { it.right() }
            var resetConversationResultIndex = 0

            withMLSTransactionReturning(Either.Right(Unit))
            withTransactionReturning(Either.Right(Unit))

            everySuspend {
                mlsContext.conversationEpoch(any())
            } returns 15UL

            everySuspend {
                conversationRepository.getConversationById(any())
            }.also {
                if (conversationResults.size == 1) {
                    it returns conversationResults.single()
                } else {
                    var conversationResultIndex = 0
                    it calls {
                        conversationResults.getOrElse(conversationResultIndex++) {
                            error("getConversationById called more times than expected")
                        }
                    }
                }
            }

            everySuspend {
                conversationRepository.resetMlsConversation(any(), any())
            } calls {
                resetConversationResults.getOrElse(resetConversationResultIndex++) {
                    error("resetMlsConversation called more times than expected")
                }
            }

            everySuspend {
                conversationRepository.fetchConversation(any())
            }.also {
                if (remoteConversationResults.size == 1) {
                    it returns remoteConversationResults.single()
                } else {
                    var remoteConversationResultIndex = 0
                    it calls {
                        remoteConversationResults.getOrElse(remoteConversationResultIndex++) {
                            error("fetchConversation called more times than expected")
                        }
                    }
                }
            }

            everySuspend {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns Unit.right()

            everySuspend {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            } returns MLSAdditionResult(emptySet(), emptySet(), emptySet()).right()

            everySuspend {
                fetchConversationUseCase(any(), any(), reason = eq(ConversationSyncReason.ConversationReset))
            } returns Unit.right()

            everySuspend {
                conversationRepository.getConversationMembers(any())
            } returns listOf(UserId("test", "test@user")).right()

            return this to ResetMLSConversationUseCaseImpl(
                selfUserId = TEST_USER_ID,
                userConfig = userConfig,
                transactionProvider = cryptoTransactionProvider,
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                fetchConversationUseCase = fetchConversationUseCase,
                kaliumConfigs = kaliumConfigs,
            )
        }
    }
}
