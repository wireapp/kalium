/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.UpdateClientCapabilitiesParam
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.user.SelfServerConfigUseCase
import kotlinx.coroutines.flow.filter

/**
 * Use case that updates the client capabilities of the current user to include the [ClientCapability.ConsumableNotifications] capability.
 * It will only update the capability if the server supports it and the client does not already have it.
 */
internal interface UpdateSelfClientCapabilityToConsumableNotificationsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class UpdateSelfClientCapabilityToConsumableNotificationsUseCaseImpl internal constructor(
    private val selfClientIdProvider: CurrentClientIdProvider,
    private val clientRepository: ClientRepository,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val selfServerConfig: SelfServerConfigUseCase,
    kaliumLogger: KaliumLogger
) : UpdateSelfClientCapabilityToConsumableNotificationsUseCase {

    private val logger = kaliumLogger.withTextTag("UpdateSelfClientCapabilityToConsumableNotificationsUseCase")

    override suspend fun invoke(): Either<CoreFailure, Unit> {
        incrementalSyncRepository.incrementalSyncState.filter { it is IncrementalSyncStatus.Live }.collect {
            val isMigrationDone = clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            val isEnabledByServer =
                (selfServerConfig() as SelfServerConfigUseCase.Result.Success).serverLinks.metaData.commonApiVersion.version >=
                        MIN_API_VERSION_FOR_CONSUMABLE_NOTIFICATIONS
            val clientHasConsumableNotifications = clientRepository.clientHasConsumableNotifications().getOrElse(false)
            if (isMigrationDone || !isEnabledByServer || clientHasConsumableNotifications) {
                logger.d(
                    "Skipping client migration: isMigrationDone: $isMigrationDone, isEnabledByServer: " +
                            "$isEnabledByServer, clientHasConsumableNotifications: $clientHasConsumableNotifications"
                )
                return@collect
            }
            selfClientIdProvider().flatMap { clientId ->
                clientRemoteRepository.updateClientCapabilities(
                    updateClientCapabilitiesParam = UpdateClientCapabilitiesParam(
                        capabilities = listOf(
                            ClientCapability.LegalHoldImplicitConsent,
                            ClientCapability.ConsumableNotifications
                        )
                    ),
                    clientID = clientId.value
                ).flatMap {
                    clientRepository.setShouldUpdateClientConsumableNotificationsCapability(false)
                    clientRepository.persistClientHasConsumableNotifications(true)
                }.onFailure {
                    logger.e("Failed to update client capabilities $it")
                }
            }.onFailure {
                logger.e("Failed to update client capabilities $it")
            }
        }
        return Unit.right()
    }
}

private const val MIN_API_VERSION_FOR_CONSUMABLE_NOTIFICATIONS = 8
