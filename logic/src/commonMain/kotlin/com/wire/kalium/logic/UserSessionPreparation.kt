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

    /** Preparation did not complete; [failure] retains the original exception. */
    public class Failure internal constructor(
        public val failure: UserSessionPreparationFailure
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

    /** Preparation stopped with [failure]. */
    public class Failed internal constructor(
        public val failure: UserSessionPreparationFailure
    ) : UserSessionPreparationState()
}

/** Actionable preparation failure that retains the original [exception]. */
public sealed class UserSessionPreparationFailure {
    public abstract val exception: Throwable

    /** The user must free storage before trying again. */
    public class InsufficientStorage internal constructor(
        public override val exception: Throwable,
    ) : UserSessionPreparationFailure()

    /** The database is temporarily busy or locked and can be tried again. */
    public class TemporarilyUnavailable internal constructor(
        public override val exception: Throwable,
    ) : UserSessionPreparationFailure()

    /** This app version cannot safely prepare the database. */
    public class ApplicationUpdateRequired internal constructor(
        public override val exception: Throwable,
    ) : UserSessionPreparationFailure()

    /** Automatic recovery is unsafe and the user needs support. */
    public class SupportRequired internal constructor(
        public override val exception: Throwable,
    ) : UserSessionPreparationFailure()
}

internal fun UserStoragePreparationFailure.toUserSessionPreparationFailure(): UserSessionPreparationFailure =
    when (this) {
        is UserStoragePreparationFailure.InsufficientStorage ->
            UserSessionPreparationFailure.InsufficientStorage(exception)

        is UserStoragePreparationFailure.TemporarilyUnavailable ->
            UserSessionPreparationFailure.TemporarilyUnavailable(exception)

        is UserStoragePreparationFailure.ApplicationUpdateRequired ->
            UserSessionPreparationFailure.ApplicationUpdateRequired(exception)

        is UserStoragePreparationFailure.SupportRequired ->
            UserSessionPreparationFailure.SupportRequired(exception)
    }
