/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.network

import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(InternalKaliumApi::class)
class RequestCallerCancellationTest {
    @Test
    fun finishingOneRequestDoesNotCancelItsCallerOrOtherRequests() = runTest {
        val source = SupervisorJob()
        val request = Job()
        val other = Job()
        val cancellation = RequestCallerCancellation(CoroutineScope(coroutineContext + source))
        val watcher = cancellation.cancelRequestWithCaller(request)
        val otherWatcher = cancellation.cancelRequestWithCaller(other)
        watcher.cancelAndJoin()
        assertTrue(source.isActive)
        assertTrue(request.isActive)
        assertTrue(other.isActive)
        otherWatcher.cancelAndJoin()
        request.cancel()
        other.cancel()
        source.cancelAndJoin()
    }

    @Test
    fun shuttingDownTheAccountCancelsItsBoundOperation() = runTest {
        val account = SupervisorJob()
        val operation = Job()
        RequestCallerCancellation(CoroutineScope(coroutineContext + account)).cancelRequestWithCaller(operation)
        account.cancelAndJoin()
        assertTrue(operation.isCancelled)
    }
}
