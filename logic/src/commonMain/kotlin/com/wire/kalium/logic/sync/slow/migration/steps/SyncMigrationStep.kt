/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.slow.migration.steps

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

/**
 * Migration step.
 * this interface provide a way to migrate the sync version of the user.
 * the logic is executed before sync itself
 * keep in mind this logic can run multiple times
 * since it runs before sync then if one of the sync steps after it failed,
 * and we need to retry sync then this logic will run again
 * @property version The sync version after executing this migration step.
 * @property invoke The migration step itself
 */
internal interface SyncMigrationStep {
    val version: Int
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}
