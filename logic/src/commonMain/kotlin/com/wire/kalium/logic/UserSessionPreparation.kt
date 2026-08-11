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

package com.wire.kalium.logic

import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.userstorage.di.UserStoragePreparationFailure

/** Result of preparing a user session for use. */
public sealed class PrepareUserSessionResult {
    /** The database is ready and [sessionScope] can be used. */
    public class Success internal constructor(
        public val sessionScope: UserSessionScope
    ) : PrepareUserSessionResult()

    /** Preparation did not complete. */
    public class Failure internal constructor(
        public val reason: UserSessionPreparationFailure
    ) : PrepareUserSessionResult()
}

/** Current in-process state of user session preparation. */
public sealed class UserSessionPreparationState {
    /** Preparation has not been requested in this process. */
    public data object NotStarted : UserSessionPreparationState()

    /** Kalium is opening the database and checking its schema version. */
    public data object OpeningDatabase : UserSessionPreparationState()

    /** SQLDelight is running the generated migration path. */
    public data object MigratingDatabase : UserSessionPreparationState()

    /** The user session is ready to use. */
    public data object Ready : UserSessionPreparationState()

    /** Preparation stopped with [reason]. */
    public class Failed internal constructor(
        public val reason: UserSessionPreparationFailure
    ) : UserSessionPreparationState()
}

/** Actionable reason why user session preparation failed. */
public sealed class UserSessionPreparationFailure {
    /** The user must free storage before trying again. */
    public data object InsufficientStorage : UserSessionPreparationFailure()

    /** The database is temporarily busy or locked and can be tried again. */
    public data object TemporarilyUnavailable : UserSessionPreparationFailure()

    /** This app version cannot safely prepare the database. */
    public data object ApplicationUpdateRequired : UserSessionPreparationFailure()

    /** Automatic recovery is unsafe and the user needs support. */
    public data object SupportRequired : UserSessionPreparationFailure()
}

internal fun UserStoragePreparationFailure.toUserSessionPreparationFailure(): UserSessionPreparationFailure =
    when (this) {
        UserStoragePreparationFailure.InsufficientStorage -> UserSessionPreparationFailure.InsufficientStorage
        UserStoragePreparationFailure.TemporarilyUnavailable -> UserSessionPreparationFailure.TemporarilyUnavailable
        UserStoragePreparationFailure.ApplicationUpdateRequired -> UserSessionPreparationFailure.ApplicationUpdateRequired
        UserStoragePreparationFailure.SupportRequired -> UserSessionPreparationFailure.SupportRequired
    }
