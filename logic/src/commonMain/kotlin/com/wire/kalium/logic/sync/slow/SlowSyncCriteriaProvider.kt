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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.sync.slow.SyncCriteriaResolution.MissingRequirement
import com.wire.kalium.logic.sync.slow.SyncCriteriaResolution.Ready
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A signal that represents the resolution
 * of all the needed for sync to happen.
 * @see Ready
 * @see MissingRequirement
 */
internal interface SyncCriteriaResolution {
    /**
     * All criteria are satisfied and Sync is free to start or keep going.
     */
    data object Ready : SyncCriteriaResolution

    /**
     * At least a criterion is not satisfied
     */
    data class MissingRequirement(val cause: String) : SyncCriteriaResolution
}

/**
 * Ingests multiple signals across the logic module
 * to signal if Sync should start or not.
 */
@Mockable
internal interface SlowSyncCriteriaProvider {

    /**
     * Returns a flow that says whether Sync
     * can be executed or should be stopped.
     */
    suspend fun syncCriteriaFlow(): Flow<SyncCriteriaResolution>
}

internal class SlowSlowSyncCriteriaProviderImpl(
    private val clientRepository: ClientRepository,
    private val logoutRepository: LogoutRepository
) : SlowSyncCriteriaProvider {

    /**
     * Returns a flow that starts with null until a Logout happens.
     * When a logout happens, the [LogoutReason] will is emitted.
     */
    private suspend fun logoutReasonFlow() = logoutRepository
        .observeLogout()
        .map<LogoutReason, LogoutReason?> { it }
        .onStart { emit(null) }

    override suspend fun syncCriteriaFlow(): Flow<SyncCriteriaResolution> =
        combine(
            logoutReasonFlow(),
            clientRepository.observeCurrentClientId(),
            clientRepository.observeIsClientRegistrationBlockedByE2EI()
        ) { logoutReason, clientId, isE2ei ->
            handleLogoutReason(logoutReason)
                ?: handleClientId(clientId)
                ?: handleIsRegistrationClientBlockedByE2EI(isE2ei)
                // All criteria are satisfied. We're ready to start sync!
                ?: Ready
        }

    /**
     * Handles the [clientId], returning a
     * [MissingRequirement] if appropriate,
     * or null otherwise.
     */
    private fun handleClientId(clientId: ClientId?) = if (clientId == null) {
        MissingRequirement("Client is not registered")
    } else {
        null
    }

    private fun handleIsRegistrationClientBlockedByE2EI(isBlocked: Boolean?) = if (isBlocked == true) {
        MissingRequirement("Client Registration Blocked: E2EI Enrollment Required")
    } else {
        null
    }

    /**
     * Handles the current [logoutReason], returning a
     * [MissingRequirement] if appropriate,
     * or null otherwise.
     */
    private fun handleLogoutReason(logoutReason: LogoutReason?): MissingRequirement? =
        when (logoutReason) {
            LogoutReason.SELF_SOFT_LOGOUT -> "Logout: SELF_SOFT_LOGOUT"
            LogoutReason.SELF_HARD_LOGOUT -> "Logout: SELF_HARD_LOGOUT"
            LogoutReason.SESSION_EXPIRED -> "Logout: SESSION_EXPIRED"
            LogoutReason.REMOVED_CLIENT -> "Logout: REMOVED_CLIENT"
            LogoutReason.DELETED_ACCOUNT -> "Logout: DELETED_ACCOUNT"
            LogoutReason.MIGRATION_TO_CC_FAILED -> "Logout: MIGRATION_TO_CC_FAILED"
            null -> null
        }?.let { MissingRequirement(it) }

}
