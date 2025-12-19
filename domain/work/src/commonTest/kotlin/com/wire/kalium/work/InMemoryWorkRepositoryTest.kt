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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryWorkRepositoryTest {

    @Test
    fun givenWorkIsUpdated_whenObservingWork_thenShouldEmitCorrectStatuses() = runTest {
        val repo = InMemoryWorkRepository()
        val id = WorkId("W1")

        // New work starts -> InProgress
        repo.addOrUpdateWork(Work(id, Work.Type.InitialSync, Work.Status.InProgress))
        repo.observeWork(id).test {
            assertEquals(Work.Status.InProgress, awaitItem())

            // Completing work removes it from the map -> observer should see Complete again
            repo.addOrUpdateWork(Work(id, Work.Type.InitialSync, Work.Status.Complete))
            assertEquals(Work.Status.Complete, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoWorkIsAdded_whenObservingWork_thenShouldEmitComplete() = runTest {
        val repo = InMemoryWorkRepository()
        val id = WorkId("NONE")

        repo.observeWork(id).test {
            // Immediately emits Complete for unknown work id
            assertEquals(Work.Status.Complete, awaitItem())
            // No changes happen — expect no further events until we cancel
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWorkIsAdded_whenObservingNewWorks_thenShouldOnlyEmitOncePerWorkID() = runTest {
        val repo = InMemoryWorkRepository()
        val id = WorkId("W2")
        val workV1 = Work(id, Work.Type.InitialSync, Work.Status.InProgress)
        val workV2 = Work(id, Work.Type.InitialSync, Work.Status.InProgress) // same id, still in progress

        repo.observeNewWorks().test {
            repo.addOrUpdateWork(workV1) // should emit
            // Should emit exactly once for the very first insertion
            assertEquals(workV1, awaitItem())

            repo.addOrUpdateWork(workV2) // should NOT emit (it's an update of same id)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
