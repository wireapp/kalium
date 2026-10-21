/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntimeOwner
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
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeMessageDeliveryTest {
    private val alert = KaliumNSEAlert("message", "conversation", "example.com", null, null, null, false)

    @Test
    fun cancelledNativeCallbackReleasesLeaseAndLateAcceptanceDoesNotAcknowledge() = runTest {
        val root = (NSTemporaryDirectory() + NSUUID().UUIDString).toPath()
        FileSystem.SYSTEM.createDirectories(root)
        val owner = AppleAccountRuntimeOwner(root.toString())
        val account = UserId("user", "example.com")
        val lease = assertIs<Either.Right<AccountRuntimeLease>>(owner.tryAcquire(account)).value
        val callback = CompletableDeferred<Continuation<Boolean>>()
        val acknowledged = mutableListOf<String>()
        val delivery = object : KaliumNSEMessageDelivery {
            override suspend fun deliver(alert: KaliumNSEAlert): Boolean =
                suspendCoroutine { callback.complete(it) }
        }
        val operation = launch {
            try {
                deliverMessageAlerts(listOf(alert, alert.copy(messageId = "later")), delivery) {
                    acknowledged.add(it.messageId)
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
            deliverMessageAlerts(listOf(alert), object : KaliumNSEMessageDelivery {
                override suspend fun deliver(alert: KaliumNSEAlert): Boolean = suspendCoroutine { completion = it }
            }) { acknowledged = true }
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
            deliverMessageAlerts(listOf(alert), object : KaliumNSEMessageDelivery {
                override suspend fun deliver(alert: KaliumNSEAlert): Boolean = suspendCoroutine { callback.complete(it) }
            }) { acknowledged = true }
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
        deliverMessageAlerts(listOf(alert, alert.copy(messageId = "rejected")), object : KaliumNSEMessageDelivery {
            override suspend fun deliver(alert: KaliumNSEAlert): Boolean = alert.messageId == "message"
        }) { acknowledged.add(it.messageId) }
        assertEquals(listOf("message"), acknowledged)
    }
}
