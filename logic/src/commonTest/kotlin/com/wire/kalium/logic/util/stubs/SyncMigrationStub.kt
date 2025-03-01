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
package com.wire.kalium.logic.util.stubs

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep
import kotlin.test.fail

class SuccessSyncMigration(
    override val version: Int

): SyncMigrationStep {
    override suspend fun invoke(): Either<CoreFailure, Unit> = Either.Right(Unit)

}

class FailureSyncMigration(
    override val version: Int

): SyncMigrationStep {
    override suspend fun invoke(): Either<CoreFailure, Unit> = Either.Left(CoreFailure.Unknown(IllegalStateException()))
}

class MigrationCrashStep(
    override val version: Int,
    private val message: String
): SyncMigrationStep {
    override suspend fun invoke(): Either<CoreFailure, Unit> = fail(message)
}
