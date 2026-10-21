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
package com.wire.kalium.work

import app.cash.turbine.test
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LongWorkScopeIntegrationTest {

    @Test
    fun givenSlowSyncFlowEmittingTrue_whenObservingNewWorkUseCase_thenShouldEmitIt() = runTest {
        val slowSyncChannel = Channel<Boolean>()
        val scope = LongWorkScope(
            userScopeProvider = { backgroundScope },
            isSlowSyncOngoingFlowProvider = { slowSyncChannel.consumeAsFlow() }
        )

        scope.observeNewWorks().test {
            slowSyncChannel.send(true)
            assertEquals(WorkId.INITIAL_SYNC, awaitItem().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSlowSyncFlowStartingStoppingAndStartingAgain_whenObservingNewWorkUseCase_thenShouldEmitCorrectly() = runTest {
        val slowSyncChannel = Channel<Boolean>()
        val scope = LongWorkScope(
            userScopeProvider = { backgroundScope },
            isSlowSyncOngoingFlowProvider = { slowSyncChannel.consumeAsFlow() }
        )

        scope.observeNewWorks().test {
            slowSyncChannel.send(true)
            assertEquals(WorkId.INITIAL_SYNC, awaitItem().id)

            slowSyncChannel.send(false)
            expectNoEvents()

            slowSyncChannel.send(true)
            assertEquals(WorkId.INITIAL_SYNC, awaitItem().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSlowSyncStarts_whenObservingWorkStatus_thenShouldBeAbleToObserveItUntilCompletion() = runTest {
        val slowSyncChannel = Channel<Boolean>()
        val scope = LongWorkScope(userScopeProvider = { backgroundScope }, isSlowSyncOngoingFlowProvider = { slowSyncChannel.consumeAsFlow() })

        scope.observeNewWorks().test {
            slowSyncChannel.send(true)
            val firstItem = awaitItem()
            assertEquals(Work.Status.InProgress, firstItem.status)
            scope.observeWorkStatus(firstItem.id).test {
                assertEquals(Work.Status.InProgress, awaitItem())

                slowSyncChannel.send(false)
                assertEquals(Work.Status.Complete, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
