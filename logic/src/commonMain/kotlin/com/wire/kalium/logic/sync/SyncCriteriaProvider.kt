package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.sync.SyncCriteriaResolution.MissingRequirement
import com.wire.kalium.logic.sync.SyncCriteriaResolution.Ready
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
    object Ready : SyncCriteriaResolution

    /**
     * At least a criterion is not satisfied
     */
    class MissingRequirement(val cause: String) : SyncCriteriaResolution
}

/**
 * Ingests multiple signals across the logic module
 * to signal if Sync should start or not.
 */
internal interface SyncCriteriaProvider {

    /**
     * Returns a flow that says whether Sync
     * can be executed or should be stopped.
     */
    suspend fun syncCriteriaFlow(): Flow<SyncCriteriaResolution>
}

internal class SyncCriteriaProviderImpl(
    private val clientRepository: ClientRepository,
    private val logoutRepository: LogoutRepository
) : SyncCriteriaProvider {

    /**
     * Returns a flow that starts with null until a Logout happens.
     * When a logout happens, the [LogoutReason] will is emitted.
     */
    private suspend fun logoutReasonFlow() = logoutRepository
        .observeLogout()
        .map<LogoutReason, LogoutReason?> { it }
        .onStart { emit(null) }

    override suspend fun syncCriteriaFlow(): Flow<SyncCriteriaResolution> =
        logoutReasonFlow().combine(clientRepository.observeCurrentClientId()) { logoutReason, clientId ->
            handleLogoutReason(logoutReason)
                ?: handleClientId(clientId)
                // All criteria are satisfied. We're ready to start sync!
                ?: SyncCriteriaResolution.Ready
        }

    /**
     * Handles the [clientId], returning a
     * [SyncCriteriaResolution.MissingRequirement] if appropriate,
     * or null otherwise.
     */
    private fun handleClientId(clientId: ClientId?) = if (clientId == null) {
        SyncCriteriaResolution.MissingRequirement("Client is not registered")
    } else {
        null
    }

    /**
     * Handles the current [logoutReason], returning a
     * [SyncCriteriaResolution.MissingRequirement] if appropriate,
     * or null otherwise.
     */
    private fun handleLogoutReason(logoutReason: LogoutReason?): SyncCriteriaResolution.MissingRequirement? =
        when (logoutReason) {
            LogoutReason.SELF_LOGOUT -> "Logout: SELF_LOGOUT"
            LogoutReason.SESSION_EXPIRED -> "Logout: SESSION_EXPIRED"
            LogoutReason.REMOVED_CLIENT -> "Logout: REMOVED_CLIENT"
            LogoutReason.DELETED_ACCOUNT -> "Logout: DELETED_ACCOUNT"
            null -> null
        }?.let { SyncCriteriaResolution.MissingRequirement(it) }

}
