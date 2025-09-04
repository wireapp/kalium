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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.feature.backup.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ResetMLSConversationUseCaseTest {

    @Test
    fun givenCompileTimeFlagDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagDisabled()
            .withRuntimeFlagEnabled()
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenCompileTimeFlagEnabledAndRuntimeFlagDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagDisabled()
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenBothFlagsEnabled_whenUseCaseCalled_thenResetConversationStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagEnabled()
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenFeatureDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureDisabled()
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenRepositorySuccess_whenUseCaseCalled_thenResetMLSConversationCalled() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TestConversation.ID)

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @Test
    fun whenUseCaseSuccess_thenLeaveConversationCalled() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TestConversation.ID)

        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }.wasInvoked()
    }

    @Test
    fun whenUseCaseInvoked_thenConversationFetchedAfterReset() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TestConversation.ID)

        coVerify {
            arrangement.fetchConversationUseCase(any(), any())
        }.wasInvoked()
    }

    @Test
    fun whenUseCaseInvoked_thenConversationEstablishedAfterReset() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TestConversation.ID)

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenMLSProtocol_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withConversation(TestConversation.MLS_CONVERSATION)
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenMixedProtocol_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withConversation(TestConversation.MIXED_CONVERSATION)
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenLeaveGroupFails_whenUseCaseCalled_thenStillSucceeds() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withLeaveGroupFailing()
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }.wasInvoked()

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenLeaveGroupSucceeds_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        val result = useCase(TestConversation.ID)

        assertTrue(result.isRight())

        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }.wasInvoked()

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }.wasInvoked()
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val userConfig = mock(UserConfigRepository::class)
        val conversationRepository = mock(ConversationRepository::class)
        val mlsConversationRepository = mock(MLSConversationRepository::class)
        val fetchConversationUseCase = mock(FetchConversationUseCase::class)
        var kaliumConfigs = KaliumConfigs(isMlsResetEnabled = true)

        fun withCompileTimeFlagDisabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = false)
        }

        fun withCompileTimeFlagEnabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = true)
        }

        suspend fun withRuntimeFlagDisabled() = apply {
            coEvery { userConfig.isMlsConversationsResetEnabled() } returns false
        }

        suspend fun withRuntimeFlagEnabled() = apply {
            coEvery { userConfig.isMlsConversationsResetEnabled() } returns true
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
            coEvery {
                conversationRepository.getConversationById(any())
            } returns conversation.right()
        }

        suspend fun withLeaveGroupFailing() = apply {
            coEvery {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns CoreFailure.Unknown(RuntimeException("Leave group failed")).left()
        }

        suspend fun arrange(): Pair<Arrangement, ResetMLSConversationUseCaseImpl> {

            withMLSTransactionReturning(Either.Right(Unit))
            withTransactionReturning(Either.Right(Unit))

            coEvery {
                conversationRepository.getConversationById(any())
            } returns TestConversation.MLS_CONVERSATION.right()

            coEvery {
                conversationRepository.resetMlsConversation(any(), any())
            } returns Unit.right()

            coEvery {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns Unit.right()

            coEvery {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            } returns MLSAdditionResult(emptySet(), emptySet()).right()

            coEvery {
                fetchConversationUseCase(any(), any())
            } returns Unit.right()

            coEvery {
                conversationRepository.getConversationMembers(any())
            } returns listOf(UserId("test", "test@user")).right()

            return this to ResetMLSConversationUseCaseImpl(
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
