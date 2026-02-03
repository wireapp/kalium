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
package com.wire.kalium.logic.sync.slow.migration.steps

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.persistence.config.UserConfigStorage
import kotlinx.coroutines.flow.first

@Suppress("ClassNaming", "MagicNumber")
internal class SyncMigrationStep_10_11(
    private val oldUserConfigStorage: Lazy<UserConfigStorage>,
    private val newUserConfigStorage: Lazy<UserConfigStorage>
) : SyncMigrationStep {

    override val version: Int = 11

    @Suppress("TooGenericExceptionCaught")
    override suspend fun invoke(): Either<CoreFailure, Unit> {
        return try {
            migrateLocallyManagedPreferences()
            Unit.right()
        } catch (e: Exception) {
            // Log but don't fail - preferences will be set by user in case of failure.
            kaliumLogger.w("Migration 10->11 failed, continuing: ${e.message}")
            Unit.right()
        }
    }

    private suspend fun migrateLocallyManagedPreferences() {
        oldUserConfigStorage.value.areReadReceiptsEnabled().first().let { isEnabled ->
            newUserConfigStorage.value.persistReadReceipts(isEnabled)
            kaliumLogger.d("Migrated read receipts: $isEnabled")
        }

        oldUserConfigStorage.value.isTypingIndicatorEnabled().first().let { isEnabled ->
            newUserConfigStorage.value.persistTypingIndicator(isEnabled)
            kaliumLogger.d("Migrated typing indicator: $isEnabled")
        }

        oldUserConfigStorage.value.isScreenshotCensoringEnabledFlow().first().let { isEnabled ->
            newUserConfigStorage.value.persistScreenshotCensoring(isEnabled)
            kaliumLogger.d("Migrated screenshot censoring: $isEnabled")
        }

        oldUserConfigStorage.value.getE2EINotificationTime()?.let { timestamp ->
            if (timestamp > 0) {
                newUserConfigStorage.value.updateE2EINotificationTime(timestamp)
                kaliumLogger.d("Migrated E2EI notification time: $timestamp")
            }
        }
    }
}
