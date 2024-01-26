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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.isActive
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.mlsmigration.hasMigrationEnded
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import kotlinx.datetime.Instant

/**
 * Updates the supported protocols of the current user.
 */
interface UpdateSelfUserSupportedProtocolsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Boolean>
}

internal class UpdateSelfUserSupportedProtocolsUseCaseImpl(
    private val clientsRepository: ClientRepository,
    private val userRepository: UserRepository,
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport
) : UpdateSelfUserSupportedProtocolsUseCase {

    override suspend operator fun invoke(): Either<CoreFailure, Boolean> {
        return if (!featureSupport.isMLSSupported) {
            kaliumLogger.d("Skip updating supported protocols, since MLS is not supported.")
            Either.Right(false)
        } else {
            (userRepository.getSelfUser()?.let { selfUser ->
                selfSupportedProtocols().flatMap { newSupportedProtocols ->
                    kaliumLogger.i(
                        "Updating supported protocols = $newSupportedProtocols previously = ${selfUser.supportedProtocols}"
                    )
                    if (newSupportedProtocols != selfUser.supportedProtocols) {
                        userRepository.updateSupportedProtocols(newSupportedProtocols).map { true }
                    } else {
                        Either.Right(false)
                    }
                }.flatMapLeft {
                    when (it) {
                        is StorageFailure.DataNotFound -> {
                            kaliumLogger.w(
                                "Skip updating supported protocols since additional protocols are not configured"
                            )
                            Either.Right(false)
                        }
                        else -> Either.Left(it)
                    }
                }
            } ?: Either.Left(StorageFailure.DataNotFound))
        }
    }

    private suspend fun selfSupportedProtocols(): Either<CoreFailure, Set<SupportedProtocol>> =
        clientsRepository.selfListOfClients().flatMap { selfClients ->
            userConfigRepository.getMigrationConfiguration()
                .flatMapLeft { if (it is StorageFailure.DataNotFound) Either.Right(MIGRATION_CONFIGURATION_DISABLED) else Either.Left(it) }
                .flatMap { migrationConfiguration ->
                    userConfigRepository.getSupportedProtocols().map { supportedProtocols ->
                        val selfSupportedProtocols = mutableSetOf<SupportedProtocol>()
                        if (proteusIsSupported(supportedProtocols, migrationConfiguration)) {
                            selfSupportedProtocols.add(SupportedProtocol.PROTEUS)
                        }

                        if (mlsIsSupported(supportedProtocols, migrationConfiguration, selfClients)) {
                            selfSupportedProtocols.add(SupportedProtocol.MLS)
                        }
                        selfSupportedProtocols
                    }
                }
        }

    private fun mlsIsSupported(
        supportedProtocols: Set<SupportedProtocol>,
        migrationConfiguration: MLSMigrationModel,
        selfClients: List<Client>
    ): Boolean {
        val mlsIsSupported = supportedProtocols.contains(SupportedProtocol.MLS)
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
        supportedProtocols: Set<SupportedProtocol>,
        migrationConfiguration: MLSMigrationModel
    ): Boolean {
        val proteusIsSupported = supportedProtocols.contains(SupportedProtocol.PROTEUS)
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
