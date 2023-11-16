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
package com.wire.kalium.persistence.dao.config

import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.config.model.E2EISettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MLSConfigDAO {
    /**
     * Save flag from the user settings to enable and disable MLS
     */
    suspend fun enableMLS(enabled: Boolean)

    /**
     * Get the saved flag to know if MLS enabled or not
     */
    suspend fun isMLSEnabled(): Boolean

    /**
     * Save MLSE2EISetting
     */
    suspend fun setE2EISettings(settingEntity: E2EISettingsEntity)

    /**
     * Get MLSE2EISetting
     */
    suspend fun getE2EISettings(): E2EISettingsEntity?

    /**
     * Get Flow of the saved MLSE2EISetting
     */
    suspend fun e2EISettingsFlow(): Flow<E2EISettingsEntity?>

    suspend fun setE2EINotificationTime(timeStamp: Long)
    suspend fun getE2EINotificationTime(): Long?
    suspend fun e2EINotificationTimeFlow(): Flow<Long?>
}

internal class MLSConfigDAOImpl internal constructor(
    private val metadataDAO: MetadataDAO
) : MLSConfigDAO {

    override suspend fun enableMLS(enabled: Boolean) =
        metadataDAO.insertBooleanValue(ENABLE_MLS, enabled)

    override suspend fun isMLSEnabled(): Boolean =
        metadataDAO.getBooleanValue(ENABLE_MLS) ?: false

    override suspend fun setE2EISettings(settingEntity: E2EISettingsEntity) {
        metadataDAO.putSerializable(E2EI_SETTINGS, settingEntity, E2EISettingsEntity.serializer())
    }

    override suspend fun getE2EISettings(): E2EISettingsEntity? =
        metadataDAO.getSerializable(E2EI_SETTINGS, E2EISettingsEntity.serializer())

    override suspend fun e2EISettingsFlow(): Flow<E2EISettingsEntity?> =
        metadataDAO.observeSerializable(E2EI_SETTINGS, E2EISettingsEntity.serializer())

    override suspend fun setE2EINotificationTime(timeStamp: Long) {
        metadataDAO.insertValue(timeStamp.toString(), E2EI_NOTIFICATION_TIME)
    }

    override suspend fun getE2EINotificationTime(): Long? =
        metadataDAO.valueByKey(E2EI_NOTIFICATION_TIME)?.toLongOrNull()

    override suspend fun e2EINotificationTimeFlow(): Flow<Long?> =
        metadataDAO.valueByKeyFlow(E2EI_NOTIFICATION_TIME).map { it?.toLongOrNull() }

    private companion object {
        const val ENABLE_MLS = "enable_mls"
        const val E2EI_SETTINGS = "end_to_end_identity_settings"
        const val E2EI_NOTIFICATION_TIME = "end_to_end_identity_notification_time"
    }
}
