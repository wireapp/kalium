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

package com.wire.kalium.logic.data.sync

import app.cash.turbine.test
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SlowSyncRepositoryTest {

    private lateinit var slowSyncRepository: SlowSyncRepository
    private val testDispatcher = TestKaliumDispatcher.default

    @BeforeTest
    fun setup() {
        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"), testDispatcher)
        slowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)
    }

    @Test
    fun givenLastInstantWasNeverSet_whenGettingLastInstant_thenTheStateIsNull() = runTest(testDispatcher) {
        // Empty Given

        val lastSyncInstant = slowSyncRepository.observeLastSlowSyncCompletionInstant().first()

        assertNull(lastSyncInstant)
    }

    @Test
    fun givenInstantIsUpdated_whenGettingTheLastSlowSyncInstant_thenShouldReturnTheNewState() = runTest(testDispatcher) {
        val instant = DateTimeUtil.currentInstant()

        slowSyncRepository.setLastSlowSyncCompletionInstant(instant)
        assertEquals(instant, slowSyncRepository.observeLastSlowSyncCompletionInstant().first())
    }

    @Test
    fun givenVersionIsUpdated_whenGettingTheLastSlowSyncVersion_thenShouldReturnTheNewState() = runTest(testDispatcher) {
        val version = 2

        slowSyncRepository.setSlowSyncVersion(version)
        assertEquals(version, slowSyncRepository.getSlowSyncVersion())
    }

    @Test
    fun givenAnInstantIsUpdated_whenObservingTheLastSlowSyncInstant_thenTheNewStateIsPropagatedForObservers() = runTest(testDispatcher) {
        val firstInstant = DateTimeUtil.currentInstant()
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

    @Test
    fun givenMLSRecoveryStatusIsUpdated_whenGettingStatus_thenTheStateMatches() = runTest(testDispatcher) {
        val newStatus = true
        slowSyncRepository.setNeedsToRecoverMLSGroups(newStatus)

        val currentState = slowSyncRepository.needsToRecoverMLSGroups()

        assertEquals(newStatus, currentState)
    }

    @Test
    fun givenMetaDataDao_whenClearLastSlowSyncCompletionInstantIsCalled_thenInvokeDeleteValueOnce() = runTest(testDispatcher) {
        slowSyncRepository.setNeedsToPersistHistoryLostMessage(true)

        val result = slowSyncRepository.needsToPersistHistoryLostMessage()

        assertTrue { result }
    }
}
