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
package com.wire.kalium.logic.data.conversation.mls

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.persistence.dao.conversation.EpochChangesDataEntity
import com.wire.kalium.persistence.dao.conversation.NameAndHandleEntity

data class NameAndHandle(
    val name: String?,
    val handle: String?
) {
    companion object {
        fun fromEntity(entity: NameAndHandleEntity) = NameAndHandle(entity.name, entity.handle)
    }
}

data class EpochChangesData(
    val conversationId: QualifiedID,
    val mlsVerificationStatus: Conversation.VerificationStatus,
    val members: Map<QualifiedID, NameAndHandle>
) {
    companion object {
        fun fromEntity(entity: EpochChangesDataEntity) = EpochChangesData(
            entity.conversationId.toModel(),
            entity.mlsVerificationStatus.toModel(),
            entity.members.map { (key, value) -> key.toModel() to NameAndHandle.fromEntity(value) }.toMap()
        )

    }
}
