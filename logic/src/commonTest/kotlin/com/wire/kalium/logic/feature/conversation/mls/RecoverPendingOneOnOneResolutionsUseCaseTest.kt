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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMockativeImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RecoverPendingOneOnOneResolutionsUseCaseTest {
    @Test
    fun givenIncrementalSyncIsNotLive_whenInvoked_thenSkipsRecovery() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withIncrementalSyncState(IncrementalSyncStatus.FetchingPendingEvents)
            .arrange()

        useCase()

        coVerify { arrangement.pendingActionsRepository.getPendingOneOnOneResolutions() }.wasNotInvoked()
        coVerify { arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(any()) }.wasNotInvoked()
    }

    @Test
    fun givenRecoverySucceeds_whenInvoked_thenAcknowledgesRecoveredUsers() = runTest {
        val pendingUserIds = listOf(TestUser.OTHER_USER_ID, TestUser.OTHER_USER_ID_2)
        val (arrangement, useCase) = Arrangement()
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withPendingUserIds(pendingUserIds)
            .withResolveResult(Either.Right(TestConversation.ID))
            .arrange()

        useCase()

        coVerify {
            arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(eq(pendingUserIds))
        }.wasInvoked(once)
    }

    @Test
    fun givenRecoveryFails_whenInvoked_thenDoesNotAcknowledgePendingUsers() = runTest {
        val pendingUserIds = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, useCase) = Arrangement()
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withPendingUserIds(pendingUserIds)
            .withResolveResult(Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        coVerify { arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(any()) }.wasNotInvoked()
    }

    @Test
    fun givenSomeRecoveriesFail_whenInvoked_thenAcknowledgesOnlySuccessfulUsers() = runTest {
        val failedUserId = TestUser.OTHER_USER_ID
        val successfulUserId = TestUser.OTHER_USER_ID_2
        val pendingUserIds = listOf(failedUserId, successfulUserId)
        val (arrangement, useCase) = Arrangement()
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withPendingUserIds(pendingUserIds)
            .withResolveResult(Either.Right(TestConversation.ID))
            .withResolveResultForUser(failedUserId, Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        coVerify {
            arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(eq(listOf(successfulUserId)))
        }.wasInvoked(once)
    }

    private class Arrangement :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMockativeImpl() {

        val pendingActionsRepository = mock(PendingActionsRepository::class)
        val incrementalSyncRepository = mock(IncrementalSyncRepository::class)
        val oneOnOneResolver = mock(OneOnOneResolver::class)

        suspend fun withIncrementalSyncState(state: IncrementalSyncStatus) = apply {
            every { incrementalSyncRepository.incrementalSyncState }.returns(flowOf(state))
        }

        suspend fun withPendingUserIds(userIds: List<UserId>) = apply {
            coEvery { pendingActionsRepository.getPendingOneOnOneResolutions() }.returns(userIds)
            coEvery { pendingActionsRepository.acknowledgePendingOneOnOneResolutions(any()) }.returns(Unit)
        }

        suspend fun withResolveResult(result: Either<CoreFailure, ConversationId>) = apply {
            withTransactionReturning(Either.Right(Unit))
            coEvery {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                    transactionContext = any(),
                    userId = any(),
                    invalidateCurrentKnownProtocols = eq(true)
                )
            }.returns(result)
        }

        suspend fun withResolveResultForUser(userId: UserId, result: Either<CoreFailure, ConversationId>) = apply {
            coEvery {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                    transactionContext = any(),
                    userId = eq(userId),
                    invalidateCurrentKnownProtocols = eq(true)
                )
            }.returns(result)
        }

        suspend fun arrange(): Pair<Arrangement, RecoverPendingOneOnOneResolutionsUseCase> = this to
            RecoverPendingOneOnOneResolutionsUseCaseImpl(
                pendingActionsRepository = pendingActionsRepository,
                incrementalSyncRepository = incrementalSyncRepository,
                transactionProvider = cryptoTransactionProvider,
                oneOnOneResolver = oneOnOneResolver
            )
    }
}
