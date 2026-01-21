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

class ObserveWorkStatusUseCaseTest {

    @Test
    fun givenWorkCompleted_whenObserving_thenFlowIsCompleted() = runTest {
        val repo = InMemoryWorkRepository()
        val useCase = ObserveWorkStatusUseCase(repo)
        val id = WorkId("X1")

        useCase.invoke(id).test {
            assertEquals(Work.Status.Complete, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenStatesOfWork_whenObserving_thenShouldEmitStatusUntilCompletion() = runTest {
        val repo = InMemoryWorkRepository()
        val useCase = ObserveWorkStatusUseCase(repo)
        val id = WorkId("X2")

        // Pre-set to InProgress so the first emission is InProgress, not Complete
        repo.addOrUpdateWork(Work(id, Work.Type.InitialSync, Work.Status.InProgress))

        useCase.invoke(id).test {
            assertEquals(Work.Status.InProgress, awaitItem())

            // Complete it -> emits Complete and closes
            repo.addOrUpdateWork(Work(id, Work.Type.InitialSync, Work.Status.Complete))
            assertEquals(Work.Status.Complete, awaitItem())
            awaitComplete()
        }
    }
}
