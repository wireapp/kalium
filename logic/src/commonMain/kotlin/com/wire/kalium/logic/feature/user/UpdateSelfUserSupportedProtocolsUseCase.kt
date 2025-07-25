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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.map
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.isActive
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.mlsmigration.hasMigrationEnded
import com.wire.kalium.logic.featureFlags.FeatureSupport
import io.mockative.Mockable
import kotlinx.datetime.Instant

/**
 * Updates the supported protocols of the current user.
 */
@Mockable
interface UpdateSelfUserSupportedProtocolsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Boolean>
}

internal class UpdateSelfUserSupportedProtocolsUseCaseImpl(
    private val clientsRepository: ClientRepository,
    private val userRepository: UserRepository,
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val logger: KaliumLogger
) : UpdateSelfUserSupportedProtocolsUseCase {

    override suspend operator fun invoke(): Either<CoreFailure, Boolean> {
        return if (!featureSupport.isMLSSupported) {
            logger.d("Skip updating supported protocols, since MLS is not supported.")
            Either.Right(false)
        } else {
            (userRepository.getSelfUser().flatMap { selfUser ->
                selfSupportedProtocols().flatMap { calculatedSupportedProtocols ->
                    val finalizedSupportedProtocols = if (selfUser.supportedProtocols?.contains(SupportedProtocol.MLS) == true) {
                        calculatedSupportedProtocols + SupportedProtocol.MLS
                    } else {
                        calculatedSupportedProtocols
                    }
                    logger.i(
                        "Updating supported protocols = $calculatedSupportedProtocols " +
                                "previously = ${selfUser.supportedProtocols}, " +
                                "finalized = $finalizedSupportedProtocols"
                    )
                    if (finalizedSupportedProtocols != selfUser.supportedProtocols) {
                        userRepository.updateSupportedProtocols(finalizedSupportedProtocols).map { true }
                    } else {
                        Either.Right(false)
                    }
                }.flatMapLeft {
                    when (it) {
                        is StorageFailure.DataNotFound -> {
                            logger.w(
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
        currentClientIdProvider().flatMap { currentClientId ->
            clientsRepository.selfListOfClients().flatMap { selfClients ->
                userConfigRepository.getMigrationConfiguration()
                    .flatMapLeft {
                        if (it is StorageFailure.DataNotFound) {
                            Either.Right(MIGRATION_CONFIGURATION_DISABLED)
                        } else {
                            Either.Left(it)
                        }
                    }
                    .flatMap { migrationConfiguration ->
                        userConfigRepository.getSupportedProtocols().map { teamSupportedProtocols ->
                            val selfSupportedProtocols = mutableSetOf<SupportedProtocol>()
                            if (proteusIsSupported(
                                    teamSettingsSupportedProtocols = teamSupportedProtocols,
                                    migrationConfiguration = migrationConfiguration
                                )
                            ) {
                                selfSupportedProtocols.add(SupportedProtocol.PROTEUS)
                            }

                            if (mlsIsSupported(
                                    teamSettingsSupportedProtocols = teamSupportedProtocols,
                                    migrationConfiguration = migrationConfiguration,
                                    selfClients = selfClients,
                                    selfClientId = currentClientId
                                )
                            ) {
                                selfSupportedProtocols.add(SupportedProtocol.MLS)
                            }
                            selfSupportedProtocols
                        }
                    }
            }
        }

    private fun mlsIsSupported(
        teamSettingsSupportedProtocols: Set<SupportedProtocol>,
        migrationConfiguration: MLSMigrationModel,
        selfClients: List<Client>,
        selfClientId: ClientId
    ): Boolean {
        val mlsIsSupported = teamSettingsSupportedProtocols.contains(SupportedProtocol.MLS)
        val mlsMigrationHasEnded = migrationConfiguration.hasMigrationEnded()
        val allSelfClientsAreMLSCapable = selfClients
            .filter { it.isActive || it.id == selfClientId }
            .ifEmpty {
                logger.w("user has 0 active clients")
                emptyList()
            }.all { it.isMLSCapable }

        logger.d(
            "mls is supported = $mlsIsSupported, " +
                    "all active self clients are mls capable = $allSelfClientsAreMLSCapable " +
                    "migration has ended = $mlsMigrationHasEnded"
        )
        return mlsIsSupported && (mlsMigrationHasEnded || allSelfClientsAreMLSCapable)
    }

    private fun proteusIsSupported(
        teamSettingsSupportedProtocols: Set<SupportedProtocol>,
        migrationConfiguration: MLSMigrationModel
    ): Boolean {
        val proteusIsSupported = teamSettingsSupportedProtocols.contains(SupportedProtocol.PROTEUS)
        val mlsMigrationHasEnded = migrationConfiguration.hasMigrationEnded()
        logger.d(
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
