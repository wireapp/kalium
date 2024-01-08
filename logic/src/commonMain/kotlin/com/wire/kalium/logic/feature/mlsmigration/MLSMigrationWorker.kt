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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.kaliumLogger

interface MLSMigrationWorker {
    suspend fun runMigration(): Either<CoreFailure, Unit>
}

internal class MLSMigrationWorkerImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureConfigRepository: FeatureConfigRepository,
    private val mlsConfigHandler: MLSConfigHandler,
    private val mlsMigrationConfigHandler: MLSMigrationConfigHandler,
    private val mlsMigrator: MLSMigrator,
) : MLSMigrationWorker {

    override suspend fun runMigration() =
        syncMigrationConfigurations().flatMap {
            userConfigRepository.getMigrationConfiguration().getOrNull()?.let { configuration ->
                if (configuration.hasMigrationStarted()) {
                    kaliumLogger.i("Running proteus to MLS migration")
                    mlsMigrator.migrateProteusConversations().flatMap {
                        if (configuration.hasMigrationEnded()) {
                            mlsMigrator.finaliseAllProteusConversations()
                        } else {
                            mlsMigrator.finaliseProteusConversations()
                        }
                    }
                } else {
                    kaliumLogger.i("MLS migration is not enabled")
                    Either.Right(Unit)
                }
            } ?: Either.Right(Unit)
        }

    private suspend fun syncMigrationConfigurations(): Either<CoreFailure, Unit> =
        featureConfigRepository.getFeatureConfigs().flatMap { configurations ->
            mlsConfigHandler.handle(configurations.mlsModel, duringSlowSync = false)
                .flatMap { configurations.mlsMigrationModel?.let {
                    mlsMigrationConfigHandler.handle(configurations.mlsMigrationModel, duringSlowSync = false)
                } ?: Either.Right(Unit) }
        }
}
