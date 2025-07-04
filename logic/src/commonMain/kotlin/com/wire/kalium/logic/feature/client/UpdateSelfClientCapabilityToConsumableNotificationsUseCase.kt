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
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.UpdateClientCapabilitiesParam
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.feature.user.SelfServerConfigUseCase
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull

/**
 * Use case that updates the client capabilities of the current user to include the [ClientCapability.ConsumableNotifications] capability.
 * It will only update the capability if the server supports it and the client does not already have it.
 */
internal interface UpdateSelfClientCapabilityToConsumableNotificationsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class UpdateSelfClientCapabilityToConsumableNotificationsUseCaseImpl internal constructor(
    private val selfClientIdProvider: CurrentClientIdProvider,
    private val clientRepository: ClientRepository,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val selfServerConfig: SelfServerConfigUseCase,
    private val syncRequester: suspend () -> Either<CoreFailure, Unit>,
    private val slowSyncRepository: SlowSyncRepository,
) : UpdateSelfClientCapabilityToConsumableNotificationsUseCase {

    private val logger = kaliumLogger.withTextTag("UpdateSelfClientCapabilityToConsumableNotificationsUseCase")

    override suspend fun invoke(): Either<CoreFailure, Unit> {
        incrementalSyncRepository.incrementalSyncState.filter { it is IncrementalSyncStatus.Live }.collect {
            val clientHasConsumableNotifications = clientRepository.observeClientHasConsumableNotifications().firstOrNull() ?: false
            val shouldUpdateCapability =
                clientHasConsumableNotifications.not() && clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            val isEnabledByServer =
                (selfServerConfig() as? SelfServerConfigUseCase.Result.Success)?.serverLinks?.metaData?.commonApiVersion?.version
                    ?.let { serverApiVersion -> serverApiVersion >= MIN_API_VERSION_FOR_CONSUMABLE_NOTIFICATIONS } ?: false

            if (!shouldUpdateCapability || !isEnabledByServer) {
                logger.d(
                    "Skipping client upgrade: shouldUpdateCapability: $shouldUpdateCapability, isEnabledByServer: " +
                            "$isEnabledByServer, clientHasConsumableNotifications: $clientHasConsumableNotifications"
                )
                return@collect
            }
            logger.d(
                "Starting client upgrade: shouldUpdateCapability: $shouldUpdateCapability, isEnabledByServer: " +
                        "$isEnabledByServer, clientHasConsumableNotifications: $clientHasConsumableNotifications"
            )
            performClientCapabilityUpgrade()
        }
        return Unit.right()
    }

    /**
     * Performs the client capability upgrade to consumable notifications.
     * After updating, it will execute a quick sync to ensure the client has the latest data.
     */
    private suspend fun performClientCapabilityUpgrade() {
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
                when (val incrementalSyncResult = syncRequester()) {
                    is Either.Left -> {
                        logger.w("Error requesting sync after updating client capabilities, will retry later: $incrementalSyncResult")
                        incrementalSyncResult
                    }

                    is Either.Right -> {
                        logger.d("Successfully requested sync after updating client capabilities")
                        finishClientCapabilityUpgrade()
                    }
                }
            }.onFailure {
                logger.e("Failed to update client capabilities $it")
            }
        }
    }

    /**
     * Finishes the client capability upgrade by setting the consumable notifications capability to true,
     * clearing the last slow sync completion instant, and requesting a sync.
     */
    private suspend fun finishClientCapabilityUpgrade(): Either<CoreFailure, Unit> {
        clientRepository.setShouldUpdateClientConsumableNotificationsCapability(false)
        clientRepository.persistClientHasConsumableNotifications(true)
        slowSyncRepository.clearLastSlowSyncCompletionInstant()
        return syncRequester()
    }
}

private const val MIN_API_VERSION_FOR_CONSUMABLE_NOTIFICATIONS = 9
