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

package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.MessageDetailsReactions
import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
data class ReactionItem(
    @SerialName("emoji") val emoji: String,
    @SerialName("count") val count: Int,
    @SerialName("isSelf") val isSelf: Boolean
)

object ReactionMapper {
    private val serializer = JsonSerializer()

    fun reactionsFromJsonString(reactionsJson: String?): ReactionsEntity {
        if (reactionsJson == null) return ReactionsEntity.EMPTY

        return try {
            val reactionItems: List<ReactionItem> = serializer.decodeFromString(reactionsJson)
            val totalReactions = reactionItems.associate { it.emoji to it.count }
            val selfUserReactions = reactionItems.filter { it.isSelf }.map { it.emoji }.toSet()
            ReactionsEntity(totalReactions, selfUserReactions)
        } catch (_: SerializationException) {
            ReactionsEntity.EMPTY
        }
    }

    @Deprecated("Use reactionsFromJsonString instead", ReplaceWith("reactionsFromJsonString(reactionsJson).totalReactions"))
    fun reactionsCountFromJsonString(allReactionJson: String?): ReactionsCountEntity =
        allReactionJson?.let {
            serializer.decodeFromString(allReactionJson)
        } ?: emptyMap()

    @Deprecated("Use reactionsFromJsonString instead", ReplaceWith("reactionsFromJsonString(reactionsJson).selfUserReactions"))
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
            availabilityStatus = userAvailabilityStatus,
            accentId = accentId
        )
    }
}
