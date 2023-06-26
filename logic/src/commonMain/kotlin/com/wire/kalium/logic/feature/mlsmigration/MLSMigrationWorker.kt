/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.data.mlsmigration.MLSMigrationRepository
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger

interface MLSMigrationWorker {
    suspend fun runMigration()
}

class MLSMigrationWorkerImpl(
    private val mlsMigrationRepository: MLSMigrationRepository,
    private val mlsMigrator: MLSMigrator,
    private val updateSupportedProtocols: UpdateSupportedProtocolsUseCase
) : MLSMigrationWorker {

    override suspend fun runMigration() {
        advanceMigration().onFailure {
            kaliumLogger.e("Failed to advance migration: $it")
        }
    }

    suspend fun advanceMigration() =
        mlsMigrationRepository.getMigrationConfiguration().getOrNull()?.let { configuration ->
            if (configuration.hasMigrationStarted()) {
                kaliumLogger.i("Running proteus to MLS migration")
                updateSupportedProtocols().flatMap {
                    mlsMigrator.migrateProteusConversations().flatMap {
                            mlsMigrator.finaliseProteusConversations()
                        }
                }
            } else {
                kaliumLogger.i("MLS migration is not enabled")
                Either.Right(Unit)
            }
        } ?: Either.Right(Unit)
}
