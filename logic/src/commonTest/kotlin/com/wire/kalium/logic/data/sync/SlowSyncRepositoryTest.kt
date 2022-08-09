package com.wire.kalium.logic.data.sync

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SlowSyncRepositoryTest {

    private lateinit var slowSyncRepository: SlowSyncRepository

    @BeforeTest
    fun setup() {
        slowSyncRepository = InMemorySlowSyncRepository()
    }

    @Test
    fun givenInstantIsUpdated_whenGettingTheLastSlowSyncInstant_thenShouldReturnTheNewState() = runTest {
        val instant = Clock.System.now()
        slowSyncRepository.setLastSlowSyncCompletionInstant(instant)

        val currentInstant = slowSyncRepository.lastFullSyncInstant.value

        assertEquals(instant, currentInstant)
    }

    @Test
    fun givenLastInstantWasNeverSet_whenGettingLastInstant_thenTheStateIsNull() = runTest {
        // Empty Given

        val state = slowSyncRepository.lastFullSyncInstant

        assertNull(state.value)
    }

    @Test
    fun givenAnInstantIsUpdated_whenObservingTheLastSlowSyncInstant_thenTheNewStateIsPropagatedForObservers() = runTest {
        val firstInstant = Clock.System.now()
        slowSyncRepository.setLastSlowSyncCompletionInstant(firstInstant)

        slowSyncRepository.lastFullSyncInstant.test {
            assertEquals(firstInstant, awaitItem())

            val secondInstant = firstInstant.plus(10.seconds)
            slowSyncRepository.setLastSlowSyncCompletionInstant(secondInstant)
            assertEquals(secondInstant, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenStatusWasNeverUpdated_whenGettingStatus_thenTheStateIsPending() = runTest {
        // Empty Given

        val currentState = slowSyncRepository.slowSyncStatus.value

        assertEquals(SlowSyncStatus.Pending, currentState)
    }

    @Test
    fun givenStatusIsUpdated_whenGettingStatus_thenTheStateIsAlsoUpdated() = runTest {
        val newStatus = SlowSyncStatus.Ongoing(SlowSyncStep.CONVERSATIONS)
        slowSyncRepository.updateSlowSyncStatus(newStatus)

        val currentState = slowSyncRepository.slowSyncStatus.value

        assertEquals(newStatus, currentState)
    }

    @Test
    fun givenStatusIsUpdated_whenObservingStatus_thenTheChangesArePropagated() = runTest {
        val firstStatus = SlowSyncStatus.Ongoing(SlowSyncStep.CONVERSATIONS)
        slowSyncRepository.updateSlowSyncStatus(firstStatus)

        slowSyncRepository.slowSyncStatus.test {
            assertEquals(firstStatus, awaitItem())

            val secondStep = SlowSyncStatus.Complete
            slowSyncRepository.updateSlowSyncStatus(secondStep)
            assertEquals(secondStep, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
