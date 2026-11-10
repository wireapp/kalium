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

/**
 * The accounts whose lock the app still holds, each under a pseudonymous key: the reasons of its holds with their age,
 * `FOREGROUND`, or `UNAVAILABLE` when the background release could not let it go.
 */
public class AccountLocks internal constructor(
    private val locked: List<Pair<String, List<String>>>,
    /** A call, upload, setup or restore still holds an account. */
    public val hasHolds: Boolean,
) {
    /** An account stays locked although nothing holds it: only the process's exit lets that lock go. */
    public val hasUnreleasable: Boolean get() = locked.any { (_, reasons) -> ACCOUNT_LOCK_UNAVAILABLE in reasons }

    /** One log line, such as `ACCOUNT_LOCKS_KEPT 1a2b3c4d=CALL@12s 9f8e7d6c=UNAVAILABLE`. */
    override fun toString(): String {
        val accounts = locked.joinToString(" ") { (key, reasons) -> "$key=${reasons.joinToString(",")}" }
        return "ACCOUNT_LOCKS_KEPT ${accounts.ifEmpty { "none" }}"
    }
}

internal const val ACCOUNT_LOCK_UNAVAILABLE = "UNAVAILABLE"
