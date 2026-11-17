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

package com.wire.kalium.logic.data.mfa

import com.wire.kalium.logic.feature.mfa.MFAOverview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Simulated account backend. One instance belongs to one Kalium account session. */
internal class InMemoryMFARepository(
    initialOverview: MFAOverview = MFAOverview.Disabled,
    private val loadOverview: (suspend () -> MFAOverview)? = null,
) : MFARepository {
    private val account = MFAAccountState(initialOverview)
    private val mutableOverview = MutableStateFlow<MFAOverview?>(null)
    override val overview = mutableOverview.asStateFlow()
    private val mutex = Mutex()
    private var hasLocalChanges = false

    override suspend fun refresh() {
        if (!mutex.tryLock()) return
        try {
            delay(DummyMFAData.OPERATION_DELAY_MILLIS)
            val loaded = loadOverview?.invoke() ?: account.overview
            if (!hasLocalChanges) account.overview = loaded
            mutableOverview.value = account.overview
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun <T> update(block: MFAAccountState.() -> T): T = mutex.withLock {
        delay(DummyMFAData.OPERATION_DELAY_MILLIS)
        val previous = account.overview
        val result = account.block()
        if (account.overview != previous) {
            hasLocalChanges = true
            mutableOverview.value = account.overview
        }
        result
    }
}
