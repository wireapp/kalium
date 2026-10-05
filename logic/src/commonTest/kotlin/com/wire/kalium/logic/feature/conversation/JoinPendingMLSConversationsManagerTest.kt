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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JoinPendingMLSConversationsManagerTest {

    @Test
    fun givenFlagIsSetAndSyncIsLive_whenObserving_thenPendingConversationsAreJoinedByExternalCommitAndFlagIsCleared() = runTest {
        val (arrangement, manager) = Arrangement()
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withShouldJoinPendingMLSConversations(flowOf(true))
            .withJoinResult(Either.Right(Unit))
            .arrange()

        manager()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversations(
                keepRetryingOnFailure = true,
                allowJoinByExternalCommit = true,
                includePendingWelcome = true,
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setShouldJoinPendingMLSConversations(false)
        }
    }

    @Test
    fun givenFlagIsNotSet_whenSyncIsLive_thenNothingIsJoined() = runTest {
        val (arrangement, manager) = Arrangement()
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withShouldJoinPendingMLSConversations(flowOf(false))
            .arrange()

        manager()

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversations(any(), any(), any())
        }
    }

    @Test
    fun givenFlagIsSet_whenSyncIsNotLive_thenNothingIsJoined() = runTest {
        val (arrangement, manager) = Arrangement()
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.FetchingPendingEvents))
            .withShouldJoinPendingMLSConversations(flowOf(true))
            .arrange()

        manager()

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversations(any(), any(), any())
        }
    }

    @Test
    fun givenJoinFails_whenObserving_thenFlagIsKeptForTheNextLiveSync() = runTest {
        val (arrangement, manager) = Arrangement()
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withShouldJoinPendingMLSConversations(flowOf(true))
            .withJoinResult(Either.Left(NetworkFailure.NoNetworkConnection(null)))
            .arrange()

        manager()

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setShouldJoinPendingMLSConversations(any())
        }
    }

    @Test
    fun givenSyncIsAlreadyLive_whenFlagIsSetLater_thenPendingConversationsAreJoined() = runTest {
        val shouldJoin = MutableStateFlow(false)
        val (arrangement, manager) = Arrangement()
            .withIncrementalSyncState(MutableStateFlow(IncrementalSyncStatus.Live))
            .withShouldJoinPendingMLSConversations(shouldJoin)
            .withJoinResult(Either.Right(Unit))
            .arrange()
        val job = launch { manager() }
        advanceUntilIdle()

        shouldJoin.value = true
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversations(any(), any(), any())
        }
        job.cancel()
    }

    private class Arrangement {
        val incrementalSyncRepository: IncrementalSyncRepository = mock()
        val userConfigRepository: UserConfigRepository = mock(MockMode.autoUnit)
        val joinExistingMLSConversations: JoinExistingMLSConversationsUseCase = mock()

        fun withIncrementalSyncState(state: Flow<IncrementalSyncStatus>) = apply {
            every { incrementalSyncRepository.incrementalSyncState } returns state
        }

        fun withShouldJoinPendingMLSConversations(shouldJoin: Flow<Boolean>) = apply {
            every { userConfigRepository.observeShouldJoinPendingMLSConversations() } returns shouldJoin
        }

        suspend fun withJoinResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { joinExistingMLSConversations(any(), any(), any()) } returns result
        }

        suspend fun arrange() = apply {
            everySuspend { userConfigRepository.setShouldJoinPendingMLSConversations(any()) } returns Either.Right(Unit)
        }.let {
            this to JoinPendingMLSConversationsManagerImpl(
                incrementalSyncRepository = incrementalSyncRepository,
                userConfigRepository = userConfigRepository,
                joinExistingMLSConversations = joinExistingMLSConversations,
                kaliumLogger = kaliumLogger,
            )
        }
    }
}
