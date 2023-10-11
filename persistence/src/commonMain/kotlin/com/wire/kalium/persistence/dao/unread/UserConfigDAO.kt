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
package com.wire.kalium.persistence.dao.unread

import com.wire.kalium.persistence.config.MLSMigrationEntity
import com.wire.kalium.persistence.config.TeamSettingsSelfDeletionStatusEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.SetSerializer

interface UserConfigDAO {

    suspend fun getTeamSettingsSelfDeletionStatus(): TeamSettingsSelfDeletionStatusEntity?
    suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatusEntity: TeamSettingsSelfDeletionStatusEntity
    )

    suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified()
    suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<TeamSettingsSelfDeletionStatusEntity?>

    suspend fun getMigrationConfiguration(): MLSMigrationEntity?
    suspend fun setMigrationConfiguration(configuration: MLSMigrationEntity)

    suspend fun getSupportedProtocols(): Set<SupportedProtocolEntity>?
    suspend fun setSupportedProtocols(protocols: Set<SupportedProtocolEntity>)
}

internal class UserConfigDAOImpl internal constructor(
    private val metadataDAO: MetadataDAO
) : UserConfigDAO {

    override suspend fun getTeamSettingsSelfDeletionStatus(): TeamSettingsSelfDeletionStatusEntity? =
        metadataDAO.getSerializable(SELF_DELETING_MESSAGES_KEY, TeamSettingsSelfDeletionStatusEntity.serializer())

    override suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatusEntity: TeamSettingsSelfDeletionStatusEntity
    ) {
        metadataDAO.putSerializable(
            key = SELF_DELETING_MESSAGES_KEY,
            value = teamSettingsSelfDeletionStatusEntity,
            TeamSettingsSelfDeletionStatusEntity.serializer()
        )
    }

    override suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified() {
        metadataDAO.getSerializable(SELF_DELETING_MESSAGES_KEY, TeamSettingsSelfDeletionStatusEntity.serializer())
            ?.copy(isStatusChanged = false)?.let { newValue ->
                metadataDAO.putSerializable(
                    SELF_DELETING_MESSAGES_KEY,
                    newValue,
                    TeamSettingsSelfDeletionStatusEntity.serializer()
                )
            }
    }

    override suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<TeamSettingsSelfDeletionStatusEntity?> =
        metadataDAO.observeSerializable(SELF_DELETING_MESSAGES_KEY, TeamSettingsSelfDeletionStatusEntity.serializer())

    override suspend fun getMigrationConfiguration(): MLSMigrationEntity? =
        metadataDAO.getSerializable(MLS_MIGRATION_KEY, MLSMigrationEntity.serializer())

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationEntity) =
        metadataDAO.putSerializable(MLS_MIGRATION_KEY, configuration, MLSMigrationEntity.serializer())

    override suspend fun getSupportedProtocols(): Set<SupportedProtocolEntity>? =
        metadataDAO.getSerializable(SUPPORTED_PROTOCOLS_KEY, SetSerializer(SupportedProtocolEntity.serializer()))

    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocolEntity>) =
        metadataDAO.putSerializable(SUPPORTED_PROTOCOLS_KEY, protocols, SetSerializer(SupportedProtocolEntity.serializer()))

    private companion object {
        private const val SELF_DELETING_MESSAGES_KEY = "SELF_DELETING_MESSAGES"
        private const val MLS_MIGRATION_KEY = "MLS_MIGRATION"
        private const val SUPPORTED_PROTOCOLS_KEY = "SUPPORTED_PROTOCOLS"
    }
}
