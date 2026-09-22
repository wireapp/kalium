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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport

/**
 * Updates the supported protocols of the current user.
 */
public interface UpdateSelfUserSupportedProtocolsUseCase {
    public suspend operator fun invoke(): UpdateSelfUserSupportedProtocolsResult
}

public sealed class UpdateSelfUserSupportedProtocolsResult {
    public data class Failure(val coreFailure: CoreFailure) : UpdateSelfUserSupportedProtocolsResult()
    public data object NotUpdated : UpdateSelfUserSupportedProtocolsResult()
    public data object Updated : UpdateSelfUserSupportedProtocolsResult()
}

/**
 * Converts the result to an Either type for internal use.
 * @return Either a CoreFailure or a Boolean indicating if the supported protocols were updated.
 */
internal fun UpdateSelfUserSupportedProtocolsResult.toEither(): Either<CoreFailure, Boolean> =
    if (this is UpdateSelfUserSupportedProtocolsResult.Failure) {
        Either.Left(this.coreFailure)
    } else {
        Either.Right(this is UpdateSelfUserSupportedProtocolsResult.Updated)
    }

internal class UpdateSelfUserSupportedProtocolsUseCaseImpl(
    private val clientsRepository: ClientRepository,
    private val userRepository: UserRepository,
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val logger: KaliumLogger
) : UpdateSelfUserSupportedProtocolsUseCase {

    override suspend operator fun invoke(): UpdateSelfUserSupportedProtocolsResult {
        return if (!featureSupport.isMLSSupported) {
            logger.d("Skip updating supported protocols, since MLS is not supported.")
            UpdateSelfUserSupportedProtocolsResult.NotUpdated
        } else {
            userRepository.getSelfUser().flatMap { selfUser ->
                selfSupportedProtocols(selfUser.supportedProtocols).flatMap { finalizedSupportedProtocols ->
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
            }.fold(
                { failure ->
                    UpdateSelfUserSupportedProtocolsResult.Failure(failure)
                },
                { updated ->
                    if (updated) UpdateSelfUserSupportedProtocolsResult.Updated else UpdateSelfUserSupportedProtocolsResult.NotUpdated
                }
            )
        }
    }

    private suspend fun selfSupportedProtocols(previous: Set<SupportedProtocol>?): Either<CoreFailure, Set<SupportedProtocol>> =
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
                            selfSupportedProtocols(
                                teamProtocols = teamSupportedProtocols,
                                migration = migrationConfiguration,
                                clients = selfClients,
                                currentClientId = currentClientId,
                                previous = previous,
                                logger = logger,
                            )
                        }
                    }
            }
        }

    companion object {
        val MIGRATION_CONFIGURATION_DISABLED = DISABLED_MLS_MIGRATION
    }
}
