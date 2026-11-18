/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)
package com.wire.kalium.logic.sync.receiving

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AccountCleanupStageTest {
    @Test
    fun `cleanup failure stays observable and preserves the primary operation error`() = runTest {
        val stages = mutableListOf<AccountCleanupStage>()
        val cleanupFailure = IllegalStateException("close failed")
        val failure = assertFailsWith<IllegalStateException> {
            observeAccountCleanup(null, stages::add) { throw cleanupFailure }
        }
        assertSame(cleanupFailure, failure)
        assertEquals(listOf(AccountCleanupStage.STARTED, AccountCleanupStage.FAILED), stages)
        stages.clear()
        val cancellation = CancellationException("cancelled")
        observeAccountCleanup(cancellation, stages::add) { throw cleanupFailure }
        assertSame(cleanupFailure, cancellation.suppressedExceptions.single())
        assertEquals(listOf(AccountCleanupStage.STARTED, AccountCleanupStage.FAILED), stages)
    }

    @Test
    fun `broken diagnostics do not prevent cleanup or replace its error`() = runTest {
        var closes = 0
        observeAccountCleanup(null, { error("observer failed") }) { closes++ }
        assertEquals(1, closes)
        val cleanupFailure = IllegalStateException("close failed")
        val failure = assertFailsWith<IllegalStateException> {
            observeAccountCleanup(null, { error("observer failed") }) { throw cleanupFailure }
        }
        assertSame(cleanupFailure, failure)
    }
}
