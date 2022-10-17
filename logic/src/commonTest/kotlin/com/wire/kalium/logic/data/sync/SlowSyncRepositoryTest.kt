package com.wire.kalium.logic.data.sync

import app.cash.turbine.test
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SlowSyncRepositoryTest {

    private lateinit var slowSyncRepository: SlowSyncRepository
    private val testDispatcher = TestKaliumDispatcher.default

    @BeforeTest
    fun setup() {
        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"), testDispatcher)
        slowSyncRepository = SlowSyncRepositoryImpl(database.provider.metadataDAO)
    }

    @Test
    fun givenInstantIsUpdated_whenGettingTheLastSlowSyncInstant_thenShouldReturnTheNewState() = runTest(testDispatcher) {
        val instant = Clock.System.now()

        slowSyncRepository.observeLastSlowSyncCompletionInstant().test {
            slowSyncRepository.setLastSlowSyncCompletionInstant(instant)
            advanceUntilIdle()
            assertEquals(instant, awaitItem())
        }
    }

    @Test
    fun givenLastInstantWasNeverSet_whenGettingLastInstant_thenTheStateIsNull() = runTest(testDispatcher) {
        // Empty Given

        val lastSyncInstant = slowSyncRepository.observeLastSlowSyncCompletionInstant().first()

        assertNull(lastSyncInstant)
    }

    @Test
    fun givenAnInstantIsUpdated_whenObservingTheLastSlowSyncInstant_thenTheNewStateIsPropagatedForObservers() = runTest(testDispatcher) {
        val firstInstant = Clock.System.now()
        slowSyncRepository.observeLastSlowSyncCompletionInstant().test {
            awaitItem() // Ignore first item
            slowSyncRepository.setLastSlowSyncCompletionInstant(firstInstant)
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
