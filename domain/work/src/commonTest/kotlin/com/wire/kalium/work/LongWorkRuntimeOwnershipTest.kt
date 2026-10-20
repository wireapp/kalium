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
@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.work

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LongWorkRuntimeOwnershipTest {
    @Test
    fun preparedOwnerDoesNotRequestWorkerInputsUntilExplicitStartAndStartsOnce() = runTest {
        var scopeRequests = 0
        var flowRequests = 0
        val slowSync = MutableSharedFlow<Boolean>()
        val owner = LongWorkScope.prepareForRuntime(
            { backgroundScope.also { scopeRequests++ } },
            { slowSync.also { flowRequests++ } },
        )
        assertEquals(0, scopeRequests)
        assertEquals(0, flowRequests)
        assertSame(owner.observeNewWorks, owner.observeNewWorks)
        assertSame(owner.observeWorkStatus, owner.observeWorkStatus)
        owner.startInitialSyncMonitoring()
        owner.startInitialSyncMonitoring()
        runCurrent()
        assertEquals(1, scopeRequests)
        assertEquals(1, flowRequests)
        assertEquals(1, slowSync.subscriptionCount.value)
        owner.observeNewWorks().test {
            slowSync.emit(true)
            val work = awaitItem()
            owner.observeWorkStatus(work.id).test {
                assertEquals(Work.Status.InProgress, awaitItem())
                slowSync.emit(false)
                assertEquals(Work.Status.Complete, awaitItem())
                awaitComplete()
            }
        }
    }

    @Test
    fun separateOwnersHaveSeparateRepositoriesAndKeepTheirSuppliedCoroutineOwners() = runTest {
        val firstFlow = MutableSharedFlow<Boolean>()
        val secondFlow = MutableSharedFlow<Boolean>()
        val first = LongWorkScope({ backgroundScope }, { firstFlow })
        val second = LongWorkScope({ backgroundScope }, { secondFlow })
        runCurrent()
        first.observeNewWorks().test {
            second.observeNewWorks().test {
                firstFlow.emit(true)
                expectNoEvents()
            }
            assertEquals(WorkId.INITIAL_SYNC, awaitItem().id)
        }
        assertEquals(1, firstFlow.subscriptionCount.value)
        assertEquals(1, secondFlow.subscriptionCount.value)
    }
}
