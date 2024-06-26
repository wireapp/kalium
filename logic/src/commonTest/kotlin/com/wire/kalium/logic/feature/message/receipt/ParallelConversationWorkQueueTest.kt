/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.message.receipt

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ParallelConversationWorkQueueTest {

    private val dispatcher = TestKaliumDispatcher

    fun runTest(test: suspend TestScope.() -> Unit) = runTest(dispatcher.main) { test() }

    private fun TestScope.testSubject() = ParallelConversationWorkQueue(
        scope = backgroundScope, // So it gets automatically cancelled once the runTest block is finished
        kaliumLogger = KaliumLogger.disabled(),
        dispatcher = dispatcher.main
    )

    @Test
    fun givenWorkIsOngoing_whenEnqueuingAnotherForTheSameConversation_theyAreExecutedInOrder() = runTest {
        val queue = testSubject()
        var startedFirst = false
        var endedFirst = false
        var startedSecond = false
        var endedSecond = false
        queue.enqueue(workInput(time = Instant.DISTANT_PAST)) {
            startedFirst = true
            delay(1.minutes)
            endedFirst = true
        }
        advanceTimeBy(50.seconds) // First is still ongoing
        assertTrue(startedFirst)
        assertFalse(endedFirst)

        // Enqueue second
        queue.enqueue(workInput(time = Instant.DISTANT_PAST + 1.seconds)) {
            assertTrue(endedFirst, "Started second before first was finished")
            startedSecond = true
            delay(1.minutes)
            endedSecond = true
        }

        advanceTimeBy(50.seconds) // First should have finished and second started
        assertTrue(endedFirst)
        assertTrue(startedSecond)
        assertFalse(endedSecond)

        advanceTimeBy(50.seconds) // Both should have finished

        advanceUntilIdle()
        assertTrue(endedFirst)
        assertTrue(endedSecond)
    }


    @Test
    fun givenWorkThrows_whenEnqueuingAnotherForTheSameConversation_itIsExecutedNormally() = runTest {
        val queue = testSubject()
        var startedFirst = false
        var endedSecond = false
        queue.enqueue(workInput(time = Instant.DISTANT_PAST)) {
            startedFirst = true
            throw IllegalStateException("Oopsie Doopsie! - Expected Test Exception")
        }
        advanceTimeBy(50.seconds) // Trigger first
        assertTrue(startedFirst)

        // Enqueue second
        queue.enqueue(workInput(time = Instant.DISTANT_PAST + 1.seconds)) {
            endedSecond = true
        }
        advanceTimeBy(50.seconds) // Both should have finished
        assertTrue(endedSecond)
    }

    @Test
    fun givenWorkIsOngoing_whenEnqueuingAnotherForDifferentConversation_theyAreExecutedInParallel() = runTest {
        val subject = testSubject()
        var startedFirst = false
        var endedFirst = false
        var startedSecond = false
        var endedSecond = false
        subject.enqueue(workInput(convIdValue = "A", time = Instant.DISTANT_PAST)) {
            startedFirst = true
            delay(1.minutes)
            endedFirst = true
        }
        advanceTimeBy(30.seconds) // First is still ongoing
        assertTrue(startedFirst)
        assertFalse(endedFirst)

        // Enqueue second
        subject.enqueue(workInput(convIdValue = "B", time = Instant.DISTANT_PAST)) {
            startedSecond = true
            delay(1.minutes)
            endedSecond = true
        }

        advanceTimeBy(5.seconds) // First should still be ongoing, but second should have started
        assertTrue(startedFirst)
        assertFalse(endedFirst)
        assertTrue(startedSecond)
        assertFalse(endedSecond)

        advanceTimeBy(2.minutes) // Both should have finished

        assertTrue(endedFirst)
        assertTrue(endedSecond)
    }

    @Test
    fun givenWorkIsOngoing_whenEnqueuingMoreEventsForSameConversation_thenOnlyTheOneWithLatestTimeIsExecutedAfterwards() = runTest {
        val queue = testSubject()
        var startedFirstCandidate = false
        var startedExpectedCandidate = false
        var completedExpectedCandidate = false
        var startedThirdCandidate = false
        queue.enqueue(workInput(time = Instant.DISTANT_PAST)) { delay(1.minutes) }
        advanceTimeBy(30.seconds) // First is still ongoing

        // Enqueue first candidate
        queue.enqueue(workInput(time = Instant.DISTANT_PAST + 10.seconds)) {
            startedFirstCandidate = true
            delay(1.minutes)
        }

        // Enqueue second candidate - expected one
        queue.enqueue(workInput(time = Instant.DISTANT_PAST + 15.seconds)) {
            startedExpectedCandidate = true
            delay(1.minutes)
            completedExpectedCandidate = true
        }

        // enqueue third candidate
        queue.enqueue(workInput(time = Instant.DISTANT_PAST + 5.seconds)) {
            startedThirdCandidate = true
            delay(1.minutes)
        }

        advanceTimeBy(1.minutes) // Finish first and start one of the candidates
        assertTrue(startedExpectedCandidate)
        assertFalse(startedFirstCandidate)
        assertFalse(startedThirdCandidate)

        advanceTimeBy(10.minutes) // Only the expected should be executed and completed. The rest shouldn't start at all.
        assertTrue(completedExpectedCandidate)
        assertFalse(startedFirstCandidate)
        assertFalse(startedThirdCandidate)
    }

    private fun workInput(
        convIdValue: String = "abc",
        time: Instant = Instant.DISTANT_PAST
    ) = ConversationTimeEventInput(ConversationId(convIdValue, "domain"), time)

}
