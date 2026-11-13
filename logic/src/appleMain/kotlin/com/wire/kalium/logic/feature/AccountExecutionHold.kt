/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps [scope]'s account open in the background for work that has execution time of its own, such as a call or a
 * background task. Release it before that time ends.
 */
public class AccountExecutionHold internal constructor(
    public val scope: UserSessionScope,
    private val endHold: () -> Unit,
    private val releaseUnlessAllowed: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var ended = false
    private var released = false

    /**
     * Ends the hold once, then releases the account unless the app is in the foreground or another hold keeps it.
     * Concurrent calls wait for that release; a failed release can be retried without ending another hold.
     */
    public suspend fun release(): Unit = withContext(NonCancellable) {
        mutex.withLock {
            if (released) return@withLock
            if (!ended) {
                endHold()
                ended = true
            }
            releaseUnlessAllowed()
            released = true
        }
    }
}
