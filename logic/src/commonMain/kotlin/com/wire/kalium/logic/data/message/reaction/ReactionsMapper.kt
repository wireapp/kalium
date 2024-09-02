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

package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.MessageDetailsReactions
import com.wire.kalium.persistence.dao.reaction.MessageReactionEntity

interface ReactionsMapper {
    fun fromDAOToEntity(messageReaction: MessageDetailsReactions): MessageReactionEntity
    fun fromEntityToModel(selfUserId: UserId, messageReactionEntity: MessageReactionEntity): MessageReaction
}

internal class ReactionsMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val domainUserTypeMapper: DomainUserTypeMapper
) : ReactionsMapper {

    override fun fromDAOToEntity(
        messageReaction: MessageDetailsReactions
    ): MessageReactionEntity = with(messageReaction) {
        MessageReactionEntity(
            emoji = emoji,
            userId = userId,
            name = name,
            handle = handle,
            previewAssetIdEntity = previewAssetId,
            userTypeEntity = userType,
            deleted = deleted,
            connectionStatus = connectionStatus,
            availabilityStatus = userAvailabilityStatus,
            accentId = accentId
        )
    }

    override fun fromEntityToModel(selfUserId: UserId, messageReactionEntity: MessageReactionEntity): MessageReaction =
        with(messageReactionEntity) {
            val messageUserId = userId.toModel()
            MessageReaction(
                emoji = emoji,
                isSelfUser = selfUserId == messageUserId,
                userSummary = UserSummary(
                    userId = messageUserId,
                    userName = name,
                    userHandle = handle,
                    userPreviewAssetId = previewAssetIdEntity?.toModel(),
                    userType = domainUserTypeMapper.fromUserTypeEntity(userTypeEntity),
                    isUserDeleted = deleted,
                    connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionStatus),
                    availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus),
                    accentId = accentId
                )
            )
        }
}
