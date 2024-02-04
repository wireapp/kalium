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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.UpdateClientCapabilitiesParam
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.filter

/**
 * Use case that updates the client capabilities of the current user to include the [ClientCapability.LegalHoldImplicitConsent] capability.
 * Once the capability is updated, the use case will not update it again.
 */
internal interface UpdateSelfClientCapabilityToLegalHoldConsentUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class UpdateSelfClientCapabilityToLegalHoldConsentUseCaseImpl internal constructor(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val userConfigRepository: UserConfigRepository,
    private val selfClientIdProvider: CurrentClientIdProvider,
    private val incrementalSyncRepository: IncrementalSyncRepository
) : UpdateSelfClientCapabilityToLegalHoldConsentUseCase {
    override suspend fun invoke(): Either<CoreFailure, Unit> {
        incrementalSyncRepository.incrementalSyncState.filter { it is IncrementalSyncStatus.Live }.collect {
            userConfigRepository.shouldUpdateClientLegalHoldCapability().takeIf { it }?.let {
                selfClientIdProvider().map { clientId ->
                    clientRemoteRepository.updateClientCapabilities(
                        UpdateClientCapabilitiesParam(listOf(ClientCapability.LegalHoldImplicitConsent)),
                        clientId.value
                    ).map {
                        userConfigRepository.setShouldUpdateClientLegalHoldCapability(false)
                    }
                }
            }
        }
        return Either.Right(Unit)
    }
}
