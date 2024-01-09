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
