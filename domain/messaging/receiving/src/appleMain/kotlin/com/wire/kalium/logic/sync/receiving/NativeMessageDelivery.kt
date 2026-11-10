/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine

/**
 * Posts each claimed message alert through the extension's host and, while the account is still open, acknowledges
 * each one the host accepted. An alert the host did not accept stays pending for the account's next owner.
 * [deliver] must use only the alert and native notification APIs, never account resources.
 * Returns true when the host accepted every alert.
 */
@InternalKaliumApi
public suspend fun <A> deliverMessageAlerts(
    alerts: List<A>,
    deliver: suspend (A) -> Boolean,
    acknowledge: suspend (A) -> Unit,
): Boolean {
    var accepted = true
    for (alert in alerts) {
        currentCoroutineContext().ensureActive()
        if (awaitNativeAcceptance { deliver(alert) }) {
            // Only the short database acknowledgement is protected, never the native callback.
            withContext(NonCancellable) { acknowledge(alert) }
        } else {
            accepted = false
        }
    }
    return accepted
}

/**
 * Swift completion handlers need not cooperate with coroutine cancellation. Detach only the native
 * enqueue (immutable alert + presenter), not any account work. Cancellation releases the waiter;
 * a late completion cannot acknowledge against a closed database. The durable alert stays pending
 * and the host deduplicates its identity if native enqueue actually succeeded.
 */
private suspend fun awaitNativeAcceptance(enqueue: suspend () -> Boolean): Boolean =
    suspendCancellableCoroutine { waiter ->
        enqueue.startCoroutine(object : Continuation<Boolean> {
            override val context = waiter.context.minusKey(Job)
            override fun resumeWith(result: Result<Boolean>) {
                waiter.resumeWith(result)
            }
        })
    }
