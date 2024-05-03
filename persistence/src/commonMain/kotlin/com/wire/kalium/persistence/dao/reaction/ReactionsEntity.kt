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

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity

data class ReactionsEntity(
    val totalReactions: ReactionsCountEntity,
    val selfUserReactions: UserReactionsEntity
) {
    companion object {
        val EMPTY = ReactionsEntity(emptyMap(), emptySet())
    }
}

data class MessageReactionEntity(
    val emoji: String,
    val userId: QualifiedIDEntity,
    val name: String?,
    val handle: String?,
    val previewAssetIdEntity: QualifiedIDEntity?,
    val userTypeEntity: UserTypeEntity,
    val deleted: Boolean,
    val connectionStatus: ConnectionEntity.State,
    val availabilityStatus: UserAvailabilityStatusEntity
)

typealias ReactionsCountEntity = Map<String, Int>
typealias UserReactionsEntity = Set<String>
