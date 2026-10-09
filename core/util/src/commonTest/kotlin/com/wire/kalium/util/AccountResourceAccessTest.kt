/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountResourceAccessTest {
    @Test
    fun pauseDrainsAdmittedWorkAndQueuesNewWorkUntilOwnershipReturns() = runTest {
        val access = AccountResourceAccess()
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = launch {
            access.withAccess {
                events += "first"
                finish.await()
                events += "finished"
            }
        }
        runCurrent()
        val pause = launch { access.pause { events += "close" } }
        runCurrent()
        val queued = launch { access.withAccess { events += "queued" } }
        runCurrent()
        assertEquals(listOf("first"), events)
        finish.complete(Unit)
        first.join()
        pause.join()
        assertFalse(queued.isCompleted)
        access.resume { events += "open" }
        queued.join()
        assertEquals(listOf("first", "finished", "close", "open", "queued"), events)
    }

    @Test
    fun nestedWorkCanFinishAfterPauseStopsAdmission() = runTest {
        val access = AccountResourceAccess()
        val next = CompletableDeferred<Unit>()
        val work = async {
            access.withAccess {
                next.await()
                access.withAccess { "result" }
            }
        }
        runCurrent()
        val pause = launch { access.pause {} }
        runCurrent()
        next.complete(Unit)
        assertEquals("result", work.await())
        pause.join()
    }

    @Test
    fun cancellingDrainLeavesResourcesOpenAndAdmitsWorkAgain() = runTest {
        val access = AccountResourceAccess()
        val finish = CompletableDeferred<Unit>()
        var closed = false
        val first = launch { access.withAccess { finish.await() } }
        runCurrent()
        val pause = launch { access.pause { closed = true } }
        runCurrent()
        pause.cancelAndJoin()
        assertFalse(closed)
        assertEquals("open", access.withAccess { "open" })
        finish.complete(Unit)
        first.join()
    }

    @Test
    fun cancellationOfAnOperationDoesNotPreventDrain() = runTest {
        val access = AccountResourceAccess()
        val work = launch { access.withAccess { CompletableDeferred<Unit>().await() } }
        runCurrent()
        work.cancelAndJoin()
        var closed = false
        access.pause { closed = true }
        assertTrue(closed)
    }

    @Test
    fun closeFailureNeverLetsWorkTouchUncertainResources() = runTest {
        val access = AccountResourceAccess()
        val failure = IllegalStateException("native close failed")
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { access.pause { throw failure } }.message)
        val accessFailure = assertFailsWith<IllegalStateException> { access.withAccess { error("must not run") } }
        assertEquals(failure.message, accessFailure.cause?.message)
        assertFailsWith<IllegalStateException> { access.resume { error("must not reopen") } }
    }

    @Test
    fun repeatedTransitionsDoNotCloseOrOpenTwice() = runTest {
        val access = AccountResourceAccess()
        val events = mutableListOf<String>()
        repeat(2) { access.pause { events += "close" } }
        repeat(2) { access.resume { events += "open" } }
        assertEquals(listOf("close", "open"), events)
    }

    @Test
    fun lifecycleChangeInsideAnOperationFailsInsteadOfDeadlocking() = runTest {
        val access = AccountResourceAccess()
        access.withAccess {
            assertFailsWith<IllegalStateException> { access.pause {} }
            assertFailsWith<IllegalStateException> { access.resume {} }
        }
    }
    @Test
    fun manualTransactionPermitAdmitsNestedWorkWhilePauseDrains() = runTest {
        val access = AccountResourceAccess()
        val finish = CompletableDeferred<Unit>()
        val transaction = async {
            val permit = access.acquirePermit()
            try {
                finish.await()
                async { access.withAccess { "nested" } }.await()
            } finally {
                permit.release()
                permit.release()
            }
        }
        runCurrent()
        val pause = launch { access.pause {} }
        runCurrent()
        val unrelated = async { access.withAccess { "new" } }
        runCurrent()
        finish.complete(Unit)
        assertEquals("nested", transaction.await())
        pause.join()
        assertFalse(unrelated.isCompleted)
        access.resume {}
        assertEquals("new", unrelated.await())
    }

    @Test
    fun lifecycleCallbacksCanUseResourcesWithoutAdmittingQueuedWork() = runTest {
        val access = AccountResourceAccess()
        access.pause { assertEquals("prepare", access.withAccess { "prepare" }) }
        val queued = async { access.withAccess { "queued" } }
        runCurrent()
        access.resume {
            assertFalse(queued.isCompleted)
            assertEquals("restore", access.withAccess { "restore" })
        }
        assertEquals("queued", queued.await())
    }

    @Test
    fun lifecycleChangeInsideManualTransactionFailsInsteadOfDeadlocking() = runTest {
        val access = AccountResourceAccess()
        val permit = access.acquirePermit()
        try {
            assertFailsWith<IllegalStateException> { access.pause {} }
            assertFailsWith<IllegalStateException> { access.resume {} }
        } finally {
            permit.release()
        }
        access.pause {}
    }

    @Test
    fun resumedOperationIsAdmittedBeforeConcurrentPause() = runTest {
        val access = AccountResourceAccess()
        access.pause {}
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val operation = launch {
            access.withResumedAccess({ events += "open" }) {
                events += "start"
                finish.await()
                events += "finish"
            }
        }
        runCurrent()
        val pause = launch { access.pause { events += "close" } }
        runCurrent()
        assertEquals(listOf("open", "start"), events)
        finish.complete(Unit)
        operation.join()
        pause.join()
        assertEquals(listOf("open", "start", "finish", "close"), events)
    }

    @Test
    fun independentlyOwnedCallbackKeepsAdmissionAfterItsCallerReturns() = runTest {
        val access = AccountResourceAccess()
        val finish = CompletableDeferred<Unit>()
        val callbackJob = SupervisorJob()
        lateinit var callback: kotlinx.coroutines.Job
        try {
            access.withAccess {
                val callbackScope = CoroutineScope(kotlinx.coroutines.currentCoroutineContext() + callbackJob)
                callback = callbackScope.launch { access.withAccess { finish.await() } }
                runCurrent()
            }
            var closed = false
            val pause = launch { access.pause { closed = true } }
            runCurrent()
            assertFalse(closed)
            finish.complete(Unit)
            callback.join()
            pause.join()
            assertTrue(closed)
        } finally {
            callbackJob.cancelAndJoin()
        }
    }

    @Test
    fun unrelatedSiblingIsNotAdmittedThroughTheCallersJob() = runTest {
        val access = AccountResourceAccess()
        val callerScope = CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
        val start = CompletableDeferred<Unit>()
        val sibling = callerScope.async {
            start.await()
            access.withAccess { "sibling" }
        }
        lateinit var pause: kotlinx.coroutines.Job
        access.withAccess {
            pause = callerScope.launch { access.pause {} }
            runCurrent()
            start.complete(Unit)
            runCurrent()
            assertFalse(sibling.isCompleted)
        }
        pause.join()
        access.resume {}
        assertEquals("sibling", sibling.await())
    }

}
