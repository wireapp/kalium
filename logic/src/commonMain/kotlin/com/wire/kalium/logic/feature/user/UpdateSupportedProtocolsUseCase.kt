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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.isActive
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.mlsmigration.hasMigrationEnded
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import kotlinx.datetime.Instant

/**
 * Updates the supported protocols of the current user.
 */
interface UpdateSupportedProtocolsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class UpdateSupportedProtocolsUseCaseImpl(
    private val clientsRepository: ClientRepository,
    private val userRepository: UserRepository,
    private val featureConfigRepository: FeatureConfigRepository
) : UpdateSupportedProtocolsUseCase {

    override suspend operator fun invoke(): Either<CoreFailure, Unit> {
        kaliumLogger.d("Updating supported protocols")

        return (userRepository.getSelfUser()?.let { selfUser ->
            supportedProtocols().flatMap { newSupportedProtocols ->
                kaliumLogger.i(
                    "Updating supported protocols = $newSupportedProtocols previously = ${selfUser.supportedProtocols}"
                )
                if (newSupportedProtocols != selfUser.supportedProtocols) {
                    userRepository.updateSupportedProtocols(newSupportedProtocols)
                } else {
                    Either.Right(Unit)
                }
            }.flatMapLeft {
                if (it is NetworkFailure.FeatureNotSupported) {
                    kaliumLogger.w(
                        "Skip updating supported protocols since it's not supported by the backend API"
                    )
                    Either.Right(Unit)
                } else {
                    Either.Left(it)
                }
            }
        } ?: Either.Left(StorageFailure.DataNotFound))
    }

    private suspend fun supportedProtocols(): Either<CoreFailure, Set<SupportedProtocol>> =
        featureConfigRepository.getFeatureConfigs().flatMap { featureConfigs ->
            clientsRepository.selfListOfClients().map { selfClients ->
                val mlsConfiguration = featureConfigs.mlsModel
                val migrationConfiguration = featureConfigs.mlsMigrationModel ?: MIGRATION_CONFIGURATION_DISABLED
                val supportedProtocols = mutableSetOf<SupportedProtocol>()
                if (proteusIsSupported(mlsConfiguration, migrationConfiguration)) {
                    supportedProtocols.add(SupportedProtocol.PROTEUS)
                }

                if (mlsIsSupported(mlsConfiguration, migrationConfiguration, selfClients)) {
                    supportedProtocols.add(SupportedProtocol.MLS)
                }
                supportedProtocols
            }
        }

    private fun mlsIsSupported(
        mlsConfiguration: MLSModel,
        migrationConfiguration: MLSMigrationModel,
        selfClients: List<Client>
    ): Boolean {
        val mlsIsSupported = mlsConfiguration.supportedProtocols.contains(SupportedProtocol.MLS)
        val mlsMigrationHasEnded = migrationConfiguration.hasMigrationEnded()
        val allSelfClientsAreMLSCapable = selfClients.filter { it.isActive }.all { it.isMLSCapable }
        kaliumLogger.d(
            "mls is supported = $mlsIsSupported, " +
                    "all active self clients are mls capable = $allSelfClientsAreMLSCapable " +
                    "migration has ended = $mlsMigrationHasEnded"
        )
        return mlsIsSupported && (mlsMigrationHasEnded || allSelfClientsAreMLSCapable)
    }

    private fun proteusIsSupported(
        mlsConfiguration: MLSModel,
        migrationConfiguration: MLSMigrationModel
    ): Boolean {
        val proteusIsSupported = mlsConfiguration.supportedProtocols.contains(SupportedProtocol.PROTEUS)
        val mlsMigrationHasEnded = migrationConfiguration.hasMigrationEnded()
        kaliumLogger.d(
            "proteus is supported = $proteusIsSupported, " +
                    "migration has ended = $mlsMigrationHasEnded"
        )
        return proteusIsSupported || !mlsMigrationHasEnded
    }

    companion object {
        val MIGRATION_CONFIGURATION_DISABLED = MLSMigrationModel(
            startTime = Instant.DISTANT_FUTURE,
            endTime = Instant.DISTANT_FUTURE,
            status = Status.DISABLED
        )
    }
}
