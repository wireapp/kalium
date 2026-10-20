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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.LifecycleCalling
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.types.Handle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class CallHandleLifecycleTest {
    @Test
    fun immediateReadyBeforeCreateReturnsPublishesHandle() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native)
        assertEquals(HANDLE, lifecycle.create { ready -> ready.ready(); HANDLE })
        assertEquals("operation", lifecycle.withHandle(HANDLE) { "operation" })
        lifecycle.release()
        assertEquals(listOf(HANDLE), native.released)
    }

    @Test
    fun jnaConvertedNumericHandleIsValidEvenWhenDataPropertyIsZero() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native)
        val convertedHandle = Handle().apply { setValue(42) }
        assertEquals(0L, convertedHandle.value)
        assertEquals(42L, convertedHandle.toLong())
        lifecycle.create { ready -> ready.ready(); convertedHandle }
        assertEquals("operation", lifecycle.withHandle(Handle(42)) { "operation" })
        assertFailsWith<IllegalStateException> { lifecycle.withHandle(Handle()) { Unit } }
        lifecycle.release()
        assertEquals(42L, native.released.single().toLong())
    }

    @Test
    fun lateReadyUnblocksInitializationAndDuplicateCallbackIsHarmless() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native)
        lateinit var ready: ReadyHandler
        val initialization = async { lifecycle.create { ready = it; HANDLE } }
        runCurrent()
        assertFalse(initialization.isCompleted)
        ready.ready()
        ready.ready()
        assertEquals(HANDLE, initialization.await())
        lifecycle.release()
    }

    @Test
    fun missingReadyTimesOutAndDrainsPublishedHandle() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native, readyTimeoutMillis = 100)
        assertFailsWith<TimeoutCancellationException> { lifecycle.create { HANDLE } }
        assertEquals(listOf(HANDLE), native.released)
        assertFailsWith<IllegalStateException> { lifecycle.withHandle(HANDLE) { error("Must not execute") } }
    }

    @Test
    fun zeroHandleFailsWithoutWaitingOrReleasingInvalidHandle() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native, readyTimeoutMillis = 100)
        assertFailsWith<IllegalStateException> { lifecycle.create { Handle(0) } }
        assertTrue(native.released.isEmpty())
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun cancellationDuringReadinessDrainsHandleAndLateReadyCannotReviveIt() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native)
        lateinit var ready: ReadyHandler
        val initialization = async { lifecycle.create { ready = it; HANDLE } }
        runCurrent()
        initialization.cancelAndJoin()
        ready.ready()
        assertEquals(listOf(HANDLE), native.released)
        assertFailsWith<CancellationException> { initialization.await() }
        assertFailsWith<IllegalStateException> { lifecycle.withHandle(HANDLE) { Unit } }
    }

    @Test
    fun failedReadinessDrainRetainsHandleForRetry() = runTest {
        val native = FakeCalling().apply { releaseFailures = 1 }
        val lifecycle = CallHandleLifecycle(native, readyTimeoutMillis = 100)
        val failure = assertFailsWith<TimeoutCancellationException> { lifecycle.create { HANDLE } }
        assertEquals(1, failure.suppressed.size)
        lifecycle.release()
        assertEquals(listOf(HANDLE, HANDLE), native.released)
        lifecycle.release()
        assertEquals(2, native.released.size)
    }

    @Test
    fun releaseTimeoutRetainsOwnershipAndConcurrentRetryOnlyReleasesOnce() = runTest {
        val native = FakeCalling().apply { releaseFailures = 1 }
        val lifecycle = CallHandleLifecycle(native)
        lifecycle.create { ready -> ready.ready(); HANDLE }
        assertFailsWith<TimeoutCancellationException> { lifecycle.release() }
        assertFailsWith<IllegalStateException> { lifecycle.withHandle(HANDLE) { Unit } }
        val acknowledgement = CompletableDeferred<Unit>()
        native.releaseAck = acknowledgement
        val first = async { lifecycle.release() }
        val second = async { lifecycle.release() }
        runCurrent()
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        acknowledgement.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf(HANDLE, HANDLE), native.released)
    }

    @Test
    fun teardownWaitsForInFlightOperationAndThenRejectsNewOperations() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native)
        lifecycle.create { ready -> ready.ready(); HANDLE }
        val operation = async { lifecycle.withHandle(HANDLE) { delay(100) } }
        runCurrent()
        val release = async { lifecycle.release() }
        runCurrent()
        assertFalse(release.isCompleted)
        advanceTimeBy(100)
        operation.await()
        release.await()
        assertEquals(listOf(HANDLE), native.released)
        assertFailsWith<IllegalStateException> { lifecycle.withHandle(HANDLE) { Unit } }
    }

    @Test
    fun teardownWaitIsBoundedWhenAnOperationHasNotFinished() = runTest {
        val native = FakeCalling()
        val lifecycle = CallHandleLifecycle(native, releaseTimeoutMillis = 100)
        lifecycle.create { ready -> ready.ready(); HANDLE }
        val operationAck = CompletableDeferred<Unit>()
        val operation = async { lifecycle.withHandle(HANDLE) { operationAck.await() } }
        runCurrent()
        assertFailsWith<TimeoutCancellationException> { lifecycle.release() }
        assertTrue(native.released.isEmpty())
        operationAck.complete(Unit)
        operation.await()
        lifecycle.release()
        assertEquals(listOf(HANDLE), native.released)
    }

    private class FakeCalling : LifecycleCalling, Calling by unusedCalling() {
        val released = mutableListOf<Handle>()
        var releaseFailures = 0
        var releaseAck: CompletableDeferred<Unit>? = null
        override fun initialize() = Unit
        override suspend fun releaseHandle(handle: Handle) {
            released.add(handle)
            if (releaseFailures > 0) {
                releaseFailures--
                withTimeout(1) { delay(10) }
            }
            releaseAck?.await()
        }
        override suspend fun endCallAndAwait(handle: Handle, conversationId: String) = error("Unused native method")
    }

    private companion object {
        val HANDLE = Handle(42)
        fun ReadyHandler.ready() = onReady(1, null)
        fun unusedCalling(): Calling = Proxy.newProxyInstance(
            Calling::class.java.classLoader,
            arrayOf(Calling::class.java)
        ) { _, method, _ -> error("Unexpected native method: ${method.name}") } as Calling
    }
}
