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
package com.wire.kalium.logic.sync.incremental

import app.cash.turbine.test
import com.wire.kalium.logic.sync.incremental.LiveSourceChangeHandlerTest.Arrangement.Companion.testScope
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LiveSourceChangeHandlerTest {
    @Test
    fun givenCreateNewCatchingUpJob_ThenSetupsOpenedDateAndStartAJob() = runTest(testScope) {
        val (_, handler) = Arrangement().arrange(this.backgroundScope)

        val taskChannel = Channel<Unit>(Channel.UNLIMITED)
        val task: () -> Unit = { taskChannel.trySend(Unit) }
        val taskFlow = taskChannel.receiveAsFlow()

        handler.startNewCatchingUpJob(1.seconds, task)

        taskFlow.test {
            advanceTimeBy(1.seconds)
            assertEquals(Unit, awaitItem(), "Task should have been executed")
            assertFalse(handler.toString().contains("websocketOpenedAt=null"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenScheduleNewCatchingUpJob_ThenShouldCancelPreviousJob() = runTest(testScope) {
        val (_, handler) = Arrangement().arrange(this.backgroundScope)

        val firstTaskChannel = Channel<Unit>(Channel.UNLIMITED)
        val secondTaskChannel = Channel<Unit>(Channel.UNLIMITED)
        val firstTask: () -> Unit = { firstTaskChannel.trySend(Unit) }
        val secondTask: () -> Unit = { secondTaskChannel.trySend(Unit) }
        val firstTaskFlow = firstTaskChannel.receiveAsFlow()
        val secondTaskFlow = secondTaskChannel.receiveAsFlow()

        // Create first job
        handler.startNewCatchingUpJob(1.seconds, firstTask)
        // advance half of the time to ensure this didn't execute yet
        advanceTimeBy(0.5.seconds)

        // Create second job
        handler.scheduleNewCatchingUpJob(1.seconds, secondTask)
        // advance the rest of the time to ensure second task executes
        advanceTimeBy(1.seconds)

        firstTaskFlow.test {
            // then first task was never executed
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        secondTaskFlow.test {
            // then second task was executed
            assertEquals(Unit, awaitItem(), "Second task should execute")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenScheduleNewCatchingUpJob_ThenSetupsLastEventReceivedDateAndStartAJob() = runTest(testScope) {
        val (_, handler) = Arrangement().arrange(this.backgroundScope)

        val taskChannel = Channel<Unit>(Channel.UNLIMITED)
        val task: () -> Unit = { taskChannel.trySend(Unit) }
        val taskFlow = taskChannel.receiveAsFlow()

        handler.scheduleNewCatchingUpJob(1.seconds, task)

        taskFlow.test {
            advanceTimeBy(1.seconds)
            assertEquals(Unit, awaitItem(), "Task should have been executed")
            assertFalse(handler.toString().contains("lastEventReceivedAt=null"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenClearScheduledJobs_ThenRestartState() = runTest(testScope) {
        val (_, handler) = Arrangement().arrange(this.backgroundScope)

        val taskChannel = Channel<Unit>(Channel.UNLIMITED)
        val task: () -> Unit = { taskChannel.trySend(Unit) }
        val taskFlow = taskChannel.receiveAsFlow()

        handler.scheduleNewCatchingUpJob(1.seconds, task)
        advanceTimeBy(1.seconds)

        taskFlow.test {
            assertEquals(Unit, awaitItem(), "Task should have been executed")
            cancelAndIgnoreRemainingEvents()
        }

        handler.clear()
        assertEquals("null", handler.toString().substringAfter("websocketOpenedAt=").substringBefore(","))
        assertEquals("null", handler.toString().substringAfter("lastEventReceivedAt=").substringBefore(","))
        assertTrue(handler.toString().contains("catchingUpJob.isActive=false"))

        // Advance time again, task should not execute
        taskFlow.test {
            advanceTimeBy(1.seconds)
            expectNoEvents() // Task should not execute after clear
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNewJobsAreCreated_ShouldHandleGracefullyWithMutex() = runTest(testScope) {
        val (_, handler) = Arrangement().arrange(this.backgroundScope)

        val taskChannel = Channel<Unit>(Channel.UNLIMITED)
        val task: () -> Unit = { taskChannel.trySend(Unit) }
        val taskFlow = taskChannel.receiveAsFlow()

        // Launch multiple jobs concurrently
        val jobs = List(10) {
            launch {
                handler.startNewCatchingUpJob(1.seconds, task)
            }
        }
        jobs.forEach { it.join() }
        // Verify that only one job is active
        assertTrue(handler.toString().contains("catchingUpJob.isActive=true"))
        // Verify websocketOpenedAt is set correctly
        assertFalse(handler.toString().contains("websocketOpenedAt=null"))

        taskFlow.test {
            advanceTimeBy(1.seconds)
            assertEquals(Unit, awaitItem(), "Task should execute once")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {

        companion object {
            val testScope = TestKaliumDispatcher.main
        }

        fun arrange(processingScope: CoroutineScope) = this to LiveSourceChangeHandlerImpl(processingScope)
    }
}
