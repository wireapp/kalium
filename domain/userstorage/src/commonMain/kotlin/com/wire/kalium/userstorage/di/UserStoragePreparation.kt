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

package com.wire.kalium.userstorage.di

/** Result of opening and, when needed, migrating a user's storage. */
public sealed interface UserStoragePreparationResult {
    /** Storage is open and ready to use. */
    public data class Success(public val storage: UserStorage) : UserStoragePreparationResult

    /** Storage preparation stopped with [reason]. */
    public class Failure internal constructor(
        public val reason: UserStoragePreparationFailure,
        internal val exception: Throwable,
    ) : UserStoragePreparationResult
}

/** Current in-process state of a user's storage. */
public sealed interface UserStorageState {
    /** Storage has not been requested in this process. */
    public data object NotStarted : UserStorageState

    /** The database is opening and its schema version is being checked. */
    public data object OpeningDatabase : UserStorageState

    /** SQLDelight is running the generated migration path. */
    public data object MigratingDatabase : UserStorageState

    /** Storage is open and ready to use. */
    public data class Ready(public val storage: UserStorage) : UserStorageState

    /** Storage preparation stopped with [reason]. */
    public data class Failed(public val reason: UserStoragePreparationFailure) : UserStorageState
}

/** Storage-level reason why opening the user database failed. */
public sealed interface UserStoragePreparationFailure {
    /** The user must free storage before trying again. */
    public data object InsufficientStorage : UserStoragePreparationFailure

    /** The database is temporarily busy or locked and can be tried again. */
    public data object TemporarilyUnavailable : UserStoragePreparationFailure

    /** This app version cannot safely open the database. */
    public data object ApplicationUpdateRequired : UserStoragePreparationFailure

    /** Automatic recovery is unsafe and the user needs support. */
    public data object SupportRequired : UserStoragePreparationFailure
}

internal val UserStoragePreparationFailure.canRetry: Boolean
    get() = this is UserStoragePreparationFailure.InsufficientStorage ||
        this is UserStoragePreparationFailure.TemporarilyUnavailable

internal fun Throwable.toUserStoragePreparationFailure(): UserStoragePreparationFailure {
    val messages = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(separator = " ")
        .lowercase()

    return when {
        messages.contains("database or disk is full") ||
            messages.contains("disk full") ||
            messages.contains("sqlite_full") -> UserStoragePreparationFailure.InsufficientStorage

        messages.contains("database is busy") ||
            messages.contains("database is locked") ||
            messages.contains("sqlite_busy") ||
            messages.contains("sqlite_locked") -> UserStoragePreparationFailure.TemporarilyUnavailable

        messages.contains("downgrade") ||
            messages.contains("newer database version") ||
            messages.contains("syntax error") ||
            messages.contains("no such table") ||
            messages.contains("no such column") -> UserStoragePreparationFailure.ApplicationUpdateRequired

        else -> UserStoragePreparationFailure.SupportRequired
    }
}
