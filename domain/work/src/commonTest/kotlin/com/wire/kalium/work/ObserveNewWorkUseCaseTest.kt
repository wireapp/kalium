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

class ObserveNewWorkUseCaseTest {

    @Test
    fun givenWorkUpdates_whenObserving_thenOnlyWorkCreationIsEmitted() = runTest {
        val repo = InMemoryWorkRepository()
        val useCase = ObserveNewWorkUseCase(repo)
        val id = WorkId("NW1")
        val w1 = Work(id, Work.Type.InitialSync, Work.Status.InProgress)
        val w1Update = Work(id, Work.Type.InitialSync, Work.Status.InProgress)

        useCase().test {
            repo.addOrUpdateWork(w1)
            assertEquals(w1, awaitItem())

            // Update same id should not emit
            repo.addOrUpdateWork(w1Update)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenMultipleWorkCreations_whenObserving_thenAllAreEmitted() = runTest {
        val repo = InMemoryWorkRepository()
        val useCase = ObserveNewWorkUseCase(repo)
        val wA = Work(WorkId("A"), Work.Type.InitialSync, Work.Status.InProgress)
        val wB = Work(WorkId("B"), Work.Type.InitialSync, Work.Status.InProgress)

        useCase().test {
            repo.addOrUpdateWork(wA)
            assertEquals(wA, awaitItem())

            repo.addOrUpdateWork(wB)
            assertEquals(wB, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
