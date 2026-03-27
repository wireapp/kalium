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
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class RecoverPendingOneOnOneResolutionsUseCaseTest {

    @Test
    fun givenSyncNotLive_whenInvoked_thenDoesNothing() = runBlocking {
        val (arrangement, useCase) = Arrangement {
            withIncrementalSyncState(flowOf(com.wire.kalium.logic.data.sync.IncrementalSyncStatus.Pending))
            withPendingUserIds(setOf(TestUser.OTHER_USER_ID))
        }.arrange()

        useCase()

        coVerify {
            arrangement.pendingOneOnOneResolutionsRepository.dequeueAll()
        }.wasNotInvoked()
        coVerify {
            arrangement.cryptoTransactionProvider.transaction<Unit>(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNoPendingUsers_whenInvoked_thenSkipsTransaction() = runBlocking {
        val (arrangement, useCase) = Arrangement {
            withIncrementalSyncState(flowOf(com.wire.kalium.logic.data.sync.IncrementalSyncStatus.Live))
            withPendingUserIds(emptySet())
        }.arrange()

        useCase()

        coVerify {
            arrangement.pendingOneOnOneResolutionsRepository.dequeueAll()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.cryptoTransactionProvider.transaction<Unit>(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenPendingUsers_whenInvoked_thenUsesSingleTransactionForAllResolutions() = runBlocking {
        val pendingUsers = setOf(TestUser.OTHER_USER_ID, TestUser.OTHER_USER_ID_2)
        val (arrangement, useCase) = Arrangement {
            withIncrementalSyncState(flowOf(com.wire.kalium.logic.data.sync.IncrementalSyncStatus.Live))
            withPendingUserIds(pendingUsers)
            withTransactionReturning(Either.Right(Unit))
            withResolveOneOnOneConversationWithUserIdReturning(TestConversation.ID.right())
        }.arrange()

        useCase()

        coVerify {
            arrangement.cryptoTransactionProvider.transaction<Unit>(eq("recoverPendingOneOnOneResolutions"), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                eq(arrangement.transactionContext),
                eq(TestUser.OTHER_USER_ID),
                eq(true)
            )
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                eq(arrangement.transactionContext),
                eq(TestUser.OTHER_USER_ID_2),
                eq(true)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFirstResolutionFails_whenInvoked_thenStopsProcessingRemainingUsers() = runBlocking {
        val pendingUsers = linkedSetOf(TestUser.OTHER_USER_ID, TestUser.OTHER_USER_ID_2)
        val failure = CoreFailure.Unknown(null)
        val (arrangement, useCase) = Arrangement {
            withIncrementalSyncState(flowOf(com.wire.kalium.logic.data.sync.IncrementalSyncStatus.Live))
            withPendingUserIds(pendingUsers)
            withTransactionReturning(Either.Right(Unit))
        }.arrange()

        coEvery {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                eq(arrangement.transactionContext),
                eq(TestUser.OTHER_USER_ID),
                eq(true)
            )
        }.returns(Either.Left(failure))

        coEvery {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                eq(arrangement.transactionContext),
                eq(TestUser.OTHER_USER_ID_2),
                eq(true)
            )
        }.returns(TestConversation.ID.right())

        useCase()

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                eq(arrangement.transactionContext),
                eq(TestUser.OTHER_USER_ID),
                eq(true)
            )
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                eq(arrangement.transactionContext),
                eq(TestUser.OTHER_USER_ID_2),
                eq(true)
            )
        }.wasNotInvoked()
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val pendingOneOnOneResolutionsRepository = mock(PendingOneOnOneResolutionsRepository::class)
        val oneOnOneResolver = mock(OneOnOneResolver::class)

        suspend fun arrange(): Pair<Arrangement, RecoverPendingOneOnOneResolutionsUseCase> = run {
            withPendingUserIds(emptySet())
            withResolveOneOnOneConversationWithUserIdReturning(TestConversation.ID.right())
            block()
            this@Arrangement to RecoverPendingOneOnOneResolutionsUseCaseImpl(
                pendingOneOnOneResolutionsRepository = pendingOneOnOneResolutionsRepository,
                incrementalSyncRepository = incrementalSyncRepository,
                transactionProvider = cryptoTransactionProvider,
                oneOnOneResolver = oneOnOneResolver
            )
        }

        suspend fun withPendingUserIds(userIds: Set<com.wire.kalium.logic.data.user.UserId>) = apply {
            coEvery { pendingOneOnOneResolutionsRepository.dequeueAll() }.returns(userIds)
        }

        suspend fun withResolveOneOnOneConversationWithUserIdReturning(
            result: Either<CoreFailure, com.wire.kalium.logic.data.id.ConversationId>
        ) = apply {
            coEvery {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(any(), any(), eq(true))
            }.returns(result)
        }
    }
}
