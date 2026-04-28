/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMockativeImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RecoverPendingMLSGroupJoinsUseCaseTest {
    @Test
    fun givenSyncDoesNotReachLive_whenInvoked_thenSkipsRecovery() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        coVerify { arrangement.pendingActionsRepository.getPendingMLSGroupJoins() }.wasNotInvoked()
        coVerify { arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(any()) }.wasNotInvoked()
    }

    @Test
    fun givenRecoverySucceeds_whenInvoked_thenAcknowledgesRecoveredConversations() = runTest {
        val pendingConversationIds = listOf(TestConversation.ID, TestConversation.id(2))
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Right(Unit))
            .withPendingConversationIds(pendingConversationIds)
            .withJoinResult(Either.Right(Unit))
            .arrange()

        useCase()

        coVerify {
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(eq(pendingConversationIds))
        }.wasInvoked(once)
    }

    @Test
    fun givenRecoveryFails_whenInvoked_thenDoesNotAcknowledgePendingConversations() = runTest {
        val pendingConversationIds = listOf(TestConversation.ID)
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Right(Unit))
            .withPendingConversationIds(pendingConversationIds)
            .withJoinResult(Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        coVerify { arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(any()) }.wasNotInvoked()
    }

    @Test
    fun givenSomeRecoveriesFail_whenInvoked_thenAcknowledgesOnlySuccessfulConversations() = runTest {
        val failedConversationId = TestConversation.ID
        val successfulConversationId = TestConversation.id(2)
        val pendingConversationIds = listOf(failedConversationId, successfulConversationId)
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Right(Unit))
            .withPendingConversationIds(pendingConversationIds)
            .withJoinResult(Either.Right(Unit))
            .withJoinResultForConversation(failedConversationId, Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        coVerify {
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(eq(listOf(successfulConversationId)))
        }.wasInvoked(once)
    }

    private class Arrangement :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMockativeImpl() {

        val pendingActionsRepository = mock(PendingActionsRepository::class)
        val syncStateObserver = mock(SyncStateObserver::class)
        val joinExistingMLSConversation = mock(JoinExistingMLSConversationUseCase::class)

        suspend fun withSyncWaitResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery { syncStateObserver.waitUntilLiveOrFailure() }.returns(result)
        }

        suspend fun withPendingConversationIds(conversationIds: List<ConversationId>) = apply {
            coEvery { pendingActionsRepository.getPendingMLSGroupJoins() }.returns(conversationIds)
            coEvery { pendingActionsRepository.acknowledgePendingMLSGroupJoins(any()) }.returns(Unit)
        }

        suspend fun withJoinResult(result: Either<CoreFailure, Unit>) = apply {
            withTransactionReturning(Either.Right(Unit))
            coEvery {
                joinExistingMLSConversation.invoke(
                    transactionContext = any(),
                    conversationId = any(),
                    mlsPublicKeys = any(),
                    allowJoinByExternalCommit = eq(true)
                )
            }.returns(result)
        }

        suspend fun withJoinResultForConversation(conversationId: ConversationId, result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                joinExistingMLSConversation.invoke(
                    transactionContext = any(),
                    conversationId = eq(conversationId),
                    mlsPublicKeys = any(),
                    allowJoinByExternalCommit = eq(true)
                )
            }.returns(result)
        }

        suspend fun arrange(): Pair<Arrangement, RecoverPendingMLSGroupJoinsUseCase> = this to
            RecoverPendingMLSGroupJoinsUseCaseImpl(
                pendingActionsRepository = pendingActionsRepository,
                syncStateObserver = syncStateObserver,
                transactionProvider = cryptoTransactionProvider,
                joinExistingMLSConversation = joinExistingMLSConversation
            )
    }
}
