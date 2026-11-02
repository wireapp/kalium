/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.network

import com.wire.kalium.util.InternalKaliumApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Retains the initiating caller when native crypto callbacks replace its Job with NonCancellable. */
@InternalKaliumApi
class RequestCallerCancellation(
    private val caller: CoroutineScope,
) : AbstractCoroutineContextElement(Key) {
    fun ensureActive() = caller.coroutineContext.ensureActive()

    /** The watcher belongs to the original caller; cancellation ends only this request. */
    fun cancelRequestWithCaller(request: Job): Job = caller.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!caller.isActive) request.cancel()
        }
    }

    companion object Key : CoroutineContext.Key<RequestCallerCancellation> {
        suspend fun capture(): RequestCallerCancellation = RequestCallerCancellation(CoroutineScope(currentCoroutineContext()))
    }
}
