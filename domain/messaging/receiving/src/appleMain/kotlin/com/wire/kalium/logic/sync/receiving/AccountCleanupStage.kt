/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.util.InternalKaliumApi

/** COMPLETED includes releasing the lease; FAILED means the runtime retains it in quarantine. */
@InternalKaliumApi
public enum class AccountCleanupStage { STARTED, COMPLETED, FAILED }

/** Observations must not replace a processing error or turn successful cleanup into a failure. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun observeAccountCleanup(
    operationFailure: Throwable?,
    observe: (AccountCleanupStage) -> Unit,
    close: suspend () -> Unit,
) {
    fun record(stage: AccountCleanupStage) {
        try { observe(stage) } catch (_: Throwable) { /* Diagnostics do not own runtime control flow. */ }
    }
    record(AccountCleanupStage.STARTED)
    try {
        close()
    } catch (failure: Throwable) {
        record(AccountCleanupStage.FAILED)
        if (operationFailure == null) throw failure
        operationFailure.addSuppressed(failure)
        return
    }
    record(AccountCleanupStage.COMPLETED)
}
