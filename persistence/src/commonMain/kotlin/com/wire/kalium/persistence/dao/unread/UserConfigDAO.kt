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

import com.wire.kalium.persistence.config.TeamSettingsSelfDeletionStatusEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow


interface UserConfigDAO {

    suspend fun getTeamSettingsSelfDeletionStatus(): TeamSettingsSelfDeletionStatusEntity?
    suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatusEntity: TeamSettingsSelfDeletionStatusEntity
    )

    suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified()
    suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<TeamSettingsSelfDeletionStatusEntity?>
}

class UserConfigDAOImpl internal constructor(
    private val metadataDAO: MetadataDAO
) : UserConfigDAO {

    override suspend fun getTeamSettingsSelfDeletionStatus(): TeamSettingsSelfDeletionStatusEntity? =
        metadataDAO.getSerializable(SELF_DELETING_MESSAGES, TeamSettingsSelfDeletionStatusEntity.serializer())


    override suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatusEntity: TeamSettingsSelfDeletionStatusEntity
    ) {
        metadataDAO.putSerializable(
            SELF_DELETING_MESSAGES,
            teamSettingsSelfDeletionStatusEntity,
            TeamSettingsSelfDeletionStatusEntity.serializer()
        )
    }

    override suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified() {
        val newValue: TeamSettingsSelfDeletionStatusEntity =
            metadataDAO.getSerializable(SELF_DELETING_MESSAGES, TeamSettingsSelfDeletionStatusEntity.serializer())
                ?.copy(isStatusChanged = false) ?: return
        metadataDAO.putSerializable(
            SELF_DELETING_MESSAGES,
            newValue,
            TeamSettingsSelfDeletionStatusEntity.serializer()
        )
    }

    override suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<TeamSettingsSelfDeletionStatusEntity?> =
        metadataDAO.observeSerializable(SELF_DELETING_MESSAGES, TeamSettingsSelfDeletionStatusEntity.serializer())

    private companion object {
        private const val SELF_DELETING_MESSAGES = "SELF_DELETING_MESSAGES"
    }
}
