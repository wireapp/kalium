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

package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.utils.CryptoServiceRegistry.record
import kotlin.concurrent.Volatile

/**
 * Records which security provider actually served each cryptographic call site in this process.
 *
 * Which implementation backs an algorithm is decided at runtime by walking the installed security
 * providers, so it varies per device, per OEM and per OS version. Rather than re-deriving that — which
 * would only ever be a claim about what a call site does — every call site reports what it was given, and
 * the debug screen displays those observations.
 *
 * A call site that has not run yet is simply absent. That is deliberate: an absent row says "not exercised
 * in this session", which is true, whereas a re-derived row could confidently state something wrong.
 *
 * Recording is a diagnostic side channel and must never affect the calling crypto path.
 */
object CryptoServiceRegistry {

    @Volatile
    private var records: Map<CryptoUsage, CryptoServiceRecord> = emptyMap()

    /**
     * Notes that [usage] was served by [record].
     *
     * Writes replace an immutable map wholesale, so a concurrent write can drop a recording rather than
     * corrupt the map. The next call from that site records it again, which is good enough for diagnostics
     * and avoids putting a lock on a crypto hot path.
     */
    fun record(usage: CryptoUsage, record: CryptoServiceRecord) {
        records = records + (usage to record)
    }

    fun recorded(): List<Pair<CryptoUsage, CryptoServiceRecord>> =
        records.let { snapshot -> CryptoUsage.entries.mapNotNull { usage -> snapshot[usage]?.let { usage to it } } }

    internal fun clear() {
        records = emptyMap()
    }
}
