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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.feature.backup.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
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

        fun withCompileTimeFlagDisabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = false)
        }

        fun withCompileTimeFlagEnabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = true)
        }

        suspend fun withRuntimeFlagDisabled() = apply {
            everySuspend { userConfig.isMlsConversationsResetEnabled() } returns false
        }

        suspend fun withRuntimeFlagEnabled() = apply {
            everySuspend { userConfig.isMlsConversationsResetEnabled() } returns true
        }

        suspend fun withFeatureDisabled() = apply {
            withCompileTimeFlagEnabled()
            withRuntimeFlagDisabled()
        }

        suspend fun withFeatureEnabled() = apply {
            withCompileTimeFlagEnabled()
            withRuntimeFlagEnabled()
        }

        suspend fun withConversation(conversation: Conversation) = apply {
            everySuspend {
                conversationRepository.getConversationById(any())
            } returns conversation.right()
        }

        suspend fun withLeaveGroupFailing() = apply {
            everySuspend {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns CoreFailure.Unknown(RuntimeException("Leave group failed")).left()
        }

        suspend fun arrange(): Pair<Arrangement, ResetMLSConversationUseCaseImpl> {

            withMLSTransactionReturning(Either.Right(Unit))
            withTransactionReturning(Either.Right(Unit))

            everySuspend {
                mlsContext.conversationEpoch(any())
            } returns 15UL

            everySuspend {
                conversationRepository.getConversationById(any())
            } returns TestConversation.MLS_CONVERSATION.right()

            everySuspend {
                conversationRepository.resetMlsConversation(any(), any())
            } returns Unit.right()

            everySuspend {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns Unit.right()

            everySuspend {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            } returns MLSAdditionResult(emptySet(), emptySet()).right()

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
