/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine

internal suspend fun deliverMessageAlerts(
    alerts: List<KaliumNSEAlert>,
    delivery: KaliumNSEMessageDelivery,
    acknowledge: suspend (NotificationMessageKey) -> Unit,
) {
    for (alert in alerts) {
        currentCoroutineContext().ensureActive()
        if (delivery.awaitNativeAcceptance(alert)) {
            // Only the short database acknowledgement is protected, never the native callback.
            withContext(NonCancellable) {
                acknowledge(
                    NotificationMessageKey(
                        alert.messageId,
                        QualifiedIDEntity(alert.conversationValue, alert.conversationDomain),
                    )
                )
            }
        }
    }
}

/**
 * Swift completion handlers need not cooperate with coroutine cancellation. Detach only the native
 * enqueue (immutable alert + presenter), not any account work. Cancellation releases the waiter;
 * a late completion cannot acknowledge against a closed database. The durable alert stays pending
 * and the host deduplicates its identity if native enqueue actually succeeded.
 */
private suspend fun KaliumNSEMessageDelivery.awaitNativeAcceptance(alert: KaliumNSEAlert): Boolean =
    suspendCancellableCoroutine { waiter ->
        val enqueue: suspend () -> Boolean = { deliver(alert) }
        enqueue.startCoroutine(object : Continuation<Boolean> {
            override val context = waiter.context.minusKey(Job)
            override fun resumeWith(result: Result<Boolean>) {
                waiter.resumeWith(result)
            }
        })
    }
