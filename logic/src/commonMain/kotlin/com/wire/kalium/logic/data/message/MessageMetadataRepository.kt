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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageMetadataDAO

interface MessageMetadataRepository {
    suspend fun originalSenderId(
        conversationId: ConversationId,
        messageId: MessageId
    ): Either<StorageFailure, UserId>
}

internal class MessageMetadataSource internal constructor(
    private val messageMetaDataDAO: MessageMetadataDAO
) : MessageMetadataRepository {
    override suspend fun originalSenderId(conversationId: ConversationId, messageId: MessageId): Either<StorageFailure, UserId> =
        wrapStorageRequest {
            messageMetaDataDAO.originalSenderId(conversationId.toDao(), messageId)
        }.map(UserIDEntity::toModel)
}
