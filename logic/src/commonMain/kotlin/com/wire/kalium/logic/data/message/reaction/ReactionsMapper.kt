package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.data.id.IdMapper
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
            availabilityStatus = userAvailabilityStatus
        )
    }

    override fun fromEntityToModel(selfUserId: UserId, messageReactionEntity: MessageReactionEntity): MessageReaction =
        with(messageReactionEntity) {
            val messageUserId = idMapper.fromDaoModel(userId)
            MessageReaction(
                emoji = emoji,
                isSelfUser = selfUserId == messageUserId,
                userSummary = UserSummary(
                    userId = messageUserId,
                    userName = name,
                    userHandle = handle,
                    userPreviewAssetId = previewAssetIdEntity?.let { idMapper.fromDaoModel(it) },
                    userType = domainUserTypeMapper.fromUserTypeEntity(userTypeEntity),
                    isUserDeleted = deleted,
                    connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionStatus),
                    availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus)
                )
            )
        }
}
