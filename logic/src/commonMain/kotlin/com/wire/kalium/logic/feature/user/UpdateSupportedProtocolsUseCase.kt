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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.mlsmigration.hasMigrationEnded
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCase.Result
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

/**
 * Updates the supported protocols of the current user.
 *
 * @return [Result.Success] if the supported protocols were updated successfully, [Result.Failure] otherwise.
 */
interface UpdateSupportedProtocolsUseCase {
    suspend operator fun invoke(): Result

    sealed interface Result {
        object Success : Result
        data class Failure(val failure: CoreFailure) : Result
    }
}

internal class UpdateSupportedProtocolsUseCaseImpl(
    private val clientsRepository: ClientRepository,
    private val userRepository: UserRepository,
    private val featureConfigRepository: FeatureConfigRepository,
    private val slowSyncRepository: SlowSyncRepository
) : UpdateSupportedProtocolsUseCase {

    override suspend operator fun invoke(): Result {
        kaliumLogger.d("Updating supported protocols")

        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

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
            }
        } ?: Either.Left(StorageFailure.DataNotFound))
            .fold(Result::Failure) { Result.Success }
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
        val allSelfClientsAreMLSCapable = selfClients.all { it.isMLSCapable }
        return mlsIsSupported && (mlsMigrationHasEnded || allSelfClientsAreMLSCapable)
    }

    private fun proteusIsSupported(
        mlsConfiguration: MLSModel,
        migrationConfiguration: MLSMigrationModel
    ): Boolean {
        val proteusIsSupported = mlsConfiguration.supportedProtocols.contains(SupportedProtocol.PROTEUS)
        val mlsMigrationHasEnded = migrationConfiguration.hasMigrationEnded()
        return proteusIsSupported || !mlsMigrationHasEnded
    }

    companion object {
        val MIGRATION_CONFIGURATION_DISABLED = MLSMigrationModel(
            startTime = Instant.DISTANT_FUTURE,
            endTime = Instant.DISTANT_FUTURE,
            usersThreshold = 0,
            clientsThreshold = 0,
            status = Status.DISABLED
        )
    }
}
