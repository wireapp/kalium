/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeMessageDeliveryTest {
    @Test
    fun cancelledNativeCallbackReleasesLeaseAndLateAcceptanceDoesNotAcknowledge() = runTest {
        val root = (NSTemporaryDirectory() + NSUUID().UUIDString).toPath()
        FileSystem.SYSTEM.createDirectories(root)
        val owner = AppleAccountRuntimeOwner(root.toString())
        val account = UserId("user", "example.com")
        val lease = assertIs<Either.Right<AccountRuntimeLease>>(owner.tryAcquire(account)).value
        val callback = CompletableDeferred<Continuation<Boolean>>()
        val acknowledged = mutableListOf<String>()
        val operation = launch {
            try {
                deliverMessageAlerts(listOf("message", "later"), { suspendCoroutine { callback.complete(it) } }) {
                    acknowledged.add(it)
                }
            } finally {
                lease.release()
            }
        }
        try {
            val completion = callback.await()
            operation.cancelAndJoin()
            assertIs<Either.Right<AccountRuntimeLease>>(
                owner.tryAcquire(account),
            ).value.release()
            completion.resume(true)
            runCurrent()
            assertEquals(emptyList(), acknowledged)
        } finally {
            operation.cancelAndJoin()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun timeoutDoesNotWaitForNativeCompletionOrAcknowledgeLateSuccess() = runTest {
        lateinit var completion: Continuation<Boolean>
        var acknowledged = false
        val result = withTimeoutOrNull(100) {
            deliverMessageAlerts(listOf("message"), { suspendCoroutine { completion = it } }) { acknowledged = true }
        }
        assertNull(result)
        completion.resume(true)
        runCurrent()
        assertEquals(false, acknowledged)
    }

    @Test
    fun lateNativeFailureAfterCancellationDoesNotTouchAccountState() = runTest {
        val callback = CompletableDeferred<Continuation<Boolean>>()
        var acknowledged = false
        val operation = launch {
            deliverMessageAlerts(listOf("message"), { suspendCoroutine { callback.complete(it) } }) { acknowledged = true }
        }
        val completion = callback.await()
        operation.cancelAndJoin()
        completion.resumeWith(Result.failure(IllegalStateException("native enqueue failed")))
        runCurrent()
        assertEquals(false, acknowledged)
    }

    @Test
    fun onlyAcceptedAlertsAreAcknowledged() = runTest {
        val acknowledged = mutableListOf<String>()
        assertFalse(deliverMessageAlerts(listOf("message", "rejected"), { it == "message" }) { acknowledged.add(it) })
        assertEquals(listOf("message"), acknowledged)
        assertTrue(deliverMessageAlerts(listOf("message"), { true }) {})
    }
}
