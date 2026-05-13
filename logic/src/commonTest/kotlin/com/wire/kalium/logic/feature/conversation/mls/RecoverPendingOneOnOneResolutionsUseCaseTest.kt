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
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RecoverPendingOneOnOneResolutionsUseCaseTest {
    @Test
    fun givenSyncDoesNotReachLive_whenInvoked_thenSkipsRecovery() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) { arrangement.pendingActionsRepository.getPendingOneOnOneResolutions() }
        verifySuspend(VerifyMode.not) { arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(any()) }
    }

    @Test
    fun givenRecoverySucceeds_whenInvoked_thenAcknowledgesRecoveredUsers() = runTest {
        val pendingUserIds = listOf(TestUser.OTHER_USER_ID, TestUser.OTHER_USER_ID_2)
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Right(Unit))
            .withPendingUserIds(pendingUserIds)
            .withResolveResult(Either.Right(TestConversation.ID))
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(eq(pendingUserIds))
        }
    }

    @Test
    fun givenRecoveryFails_whenInvoked_thenDoesNotAcknowledgePendingUsers() = runTest {
        val pendingUserIds = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Right(Unit))
            .withPendingUserIds(pendingUserIds)
            .withResolveResult(Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) { arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(any()) }
    }

    @Test
    fun givenSomeRecoveriesFail_whenInvoked_thenAcknowledgesOnlySuccessfulUsers() = runTest {
        val failedUserId = TestUser.OTHER_USER_ID
        val successfulUserId = TestUser.OTHER_USER_ID_2
        val pendingUserIds = listOf(failedUserId, successfulUserId)
        val (arrangement, useCase) = Arrangement()
            .withSyncWaitResult(Either.Right(Unit))
            .withPendingUserIds(pendingUserIds)
            .withResolveResult(Either.Right(TestConversation.ID))
            .withResolveResultForUser(failedUserId, Either.Left(CoreFailure.Unknown(RuntimeException("boom"))))
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionsRepository.acknowledgePendingOneOnOneResolutions(eq(listOf(successfulUserId)))
        }
    }

    private class Arrangement :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val pendingActionsRepository = mock<PendingActionsRepository>(mode = MockMode.autoUnit)
        val syncStateObserver = mock<SyncStateObserver>(mode = MockMode.autoUnit)
        val oneOnOneResolver = mock<OneOnOneResolver>(mode = MockMode.autoUnit)

        suspend fun withSyncWaitResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { syncStateObserver.waitUntilLiveOrFailure() } returns result
        }

        suspend fun withPendingUserIds(userIds: List<UserId>) = apply {
            everySuspend { pendingActionsRepository.getPendingOneOnOneResolutions() } returns userIds
            everySuspend { pendingActionsRepository.acknowledgePendingOneOnOneResolutions(any()) } returns Unit
        }

        suspend fun withResolveResult(result: Either<CoreFailure, ConversationId>) = apply {
            withTransactionReturning(Either.Right(Unit))
            everySuspend {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                    transactionContext = any(),
                    userId = any(),
                    invalidateCurrentKnownProtocols = eq(true)
                )
            } returns result
        }

        suspend fun withResolveResultForUser(userId: UserId, result: Either<CoreFailure, ConversationId>) = apply {
            everySuspend {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                    transactionContext = any(),
                    userId = eq(userId),
                    invalidateCurrentKnownProtocols = eq(true)
                )
            } returns result
        }

        suspend fun arrange(): Pair<Arrangement, RecoverPendingOneOnOneResolutionsUseCase> = this to
            RecoverPendingOneOnOneResolutionsUseCaseImpl(
                pendingActionsRepository = pendingActionsRepository,
                syncStateObserver = syncStateObserver,
                transactionProvider = cryptoTransactionProvider,
                oneOnOneResolver = oneOnOneResolver
            )
    }
}
