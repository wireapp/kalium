package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.MessageDetailsReactions
import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.serialization.decodeFromString

object ReactionMapper {
    private val serializer = JsonSerializer()
    fun reactionsCountFromJsonString(allReactionJson: String?): ReactionsCountEntity =
        allReactionJson?.let {
            serializer.decodeFromString(allReactionJson)
        } ?: emptyMap()

    fun userReactionsFromJsonString(userReactionsJson: String?): UserReactionsEntity =
        userReactionsJson?.let {
            serializer.decodeFromString(userReactionsJson)
        } ?: emptySet()

    fun fromDAOToMessageReactionsEntity(
        queryResult: MessageDetailsReactions
    ): MessageReactionEntity = with(queryResult) {
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
}
