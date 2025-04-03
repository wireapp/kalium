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

package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Suppress("LongParameterList", "TooManyFunctions")
interface ClientRegistrationStorage {
    suspend fun getRegisteredClientId(): String?
    suspend fun setRegisteredClientId(registeredClientId: String)
    suspend fun observeRegisteredClientId(): Flow<String?>
    suspend fun setRetainedClientId(retainedClientId: String)
    suspend fun getRetainedClientId(): String?
    suspend fun clearRegisteredClientId()
    suspend fun clearRetainedClientId()
    suspend fun hasRegisteredMLSClient(): Boolean
    suspend fun setHasRegisteredMLSClient()
    suspend fun observeIsClientRegistrationBlockedByE2EI(): Flow<Boolean>
    suspend fun setClientRegistrationBlockedByE2EI()
    suspend fun clearClientRegistrationBlockedByE2EI()
    suspend fun clearHasRegisteredMLSClient()
    suspend fun clearClientHasConsumableNotifications()
    suspend fun isBlockedByE2EI(): Boolean
    suspend fun setHasConsumableNotifications(hasConsumableNotifications: Boolean)
    suspend fun observeHasConsumableNotifications(): Flow<Boolean>
}

@Suppress("LongParameterList", "TooManyFunctions")
class ClientRegistrationStorageImpl(private val metadataDAO: MetadataDAO) : ClientRegistrationStorage {

    override suspend fun getRegisteredClientId(): String? = observeRegisteredClientId().first()

    override suspend fun setRegisteredClientId(registeredClientId: String) {
        metadataDAO.insertValue(registeredClientId, REGISTERED_CLIENT_ID_KEY)
        metadataDAO.insertValue(registeredClientId, RETAINED_CLIENT_ID_KEY)
    }

    override suspend fun observeRegisteredClientId(): Flow<String?> = metadataDAO.valueByKeyFlow(REGISTERED_CLIENT_ID_KEY)
    override suspend fun setRetainedClientId(retainedClientId: String) = metadataDAO.insertValue(retainedClientId, RETAINED_CLIENT_ID_KEY)
    override suspend fun getRetainedClientId(): String? = metadataDAO.valueByKey(RETAINED_CLIENT_ID_KEY)
    override suspend fun clearRegisteredClientId() = metadataDAO.deleteValue(REGISTERED_CLIENT_ID_KEY)
    override suspend fun clearRetainedClientId() = metadataDAO.deleteValue(RETAINED_CLIENT_ID_KEY)
    override suspend fun hasRegisteredMLSClient(): Boolean = metadataDAO.valueByKey(HAS_REGISTERED_MLS_CLIENT_KEY).toBoolean()
    override suspend fun setHasRegisteredMLSClient() = metadataDAO.insertValue(true.toString(), HAS_REGISTERED_MLS_CLIENT_KEY)
    override suspend fun observeIsClientRegistrationBlockedByE2EI(): Flow<Boolean> =
        metadataDAO.valueByKeyFlow(CLIENT_REGISTRATION_BLOCKED_BY_E2EI).map {
            it.toBoolean() && !it.isNullOrEmpty()
        }

    override suspend fun isBlockedByE2EI(): Boolean = metadataDAO.valueByKey(CLIENT_REGISTRATION_BLOCKED_BY_E2EI).toBoolean()
    override suspend fun setHasConsumableNotifications(hasConsumableNotifications: Boolean) {
        metadataDAO.insertValue(hasConsumableNotifications.toString(), CLIENT_HAS_CONSUMABLE_NOTIFICATIONS)
    }

    override suspend fun observeHasConsumableNotifications(): Flow<Boolean> =
        metadataDAO.valueByKeyFlow(CLIENT_HAS_CONSUMABLE_NOTIFICATIONS).map {
            it.toBoolean() && !it.isNullOrEmpty()
        }

    override suspend fun setClientRegistrationBlockedByE2EI() =
        metadataDAO.insertValue(true.toString(), CLIENT_REGISTRATION_BLOCKED_BY_E2EI)

    override suspend fun clearClientRegistrationBlockedByE2EI() = metadataDAO.deleteValue(CLIENT_REGISTRATION_BLOCKED_BY_E2EI)

    override suspend fun clearHasRegisteredMLSClient() = metadataDAO.deleteValue(HAS_REGISTERED_MLS_CLIENT_KEY)
    override suspend fun clearClientHasConsumableNotifications() {
        metadataDAO.deleteValue(CLIENT_HAS_CONSUMABLE_NOTIFICATIONS)
    }

    companion object {
        private const val REGISTERED_CLIENT_ID_KEY = "registered_client_id"
        const val RETAINED_CLIENT_ID_KEY = "retained_client_id"
        private const val HAS_REGISTERED_MLS_CLIENT_KEY = "has_registered_mls_client"
        private const val CLIENT_REGISTRATION_BLOCKED_BY_E2EI = "client_registration_blocked_by_e2ei"
        private const val CLIENT_HAS_CONSUMABLE_NOTIFICATIONS = "client_has_consumable_notifications"
    }
}
