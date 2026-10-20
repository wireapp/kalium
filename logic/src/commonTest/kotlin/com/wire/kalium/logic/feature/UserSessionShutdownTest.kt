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
package com.wire.kalium.logic.feature

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.logic.di.ConsumerTestDependencies
import com.wire.kalium.logic.di.KaliumConsumerRegistry
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserSessionShutdownTest {
    @Test
    fun consumerDrainTimeoutDoesNotLeaveSessionWorkersRunningOrResolveUnusedResources() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dependencies = ConsumerTestDependencies(dispatcher.testKaliumDispatcher())
        val entered = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        dependencies.read = {
            entered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { finishCleanup.await() }
            }
        }
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val caller = Job()
        val operation = launch(caller) { consumer.getMessageById(TestConversation.ID, "message") }
        entered.await()
        val sessionJob = SupervisorJob()
        var workerCancelled = false
        val worker = CoroutineScope(sessionJob + dispatcher).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                workerCancelled = true
            }
        }
        val cells = lazy<CellsScope> { error("Unused Cells must not be resolved during shutdown") }
        val calling = lazy<CallManager> { error("Unused calling must not be resolved during shutdown") }
        try {
            assertFailsWith<TimeoutCancellationException> {
                UserSessionShutdown(registry, sessionJob, cells, calling).stop()
            }
            assertTrue(sessionJob.isCancelled)
            assertTrue(workerCancelled)
            assertTrue(worker.isCompleted)
            assertFalse(operation.isCompleted)
            assertTrue(caller.isActive)
            assertFalse(cells.isInitialized())
            assertFalse(calling.isInitialized())
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finishCleanup.complete(Unit)
            operation.join()
            registry.invalidateAndJoin()
            caller.cancel()
            sessionJob.cancel()
        }
    }
}
