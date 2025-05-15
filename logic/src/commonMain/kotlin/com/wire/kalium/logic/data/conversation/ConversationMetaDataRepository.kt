/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.conversation.ConversationMetaDataDAO


interface ConversationMetaDataRepository {
    suspend fun getConversationTypeAndProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Pair<Conversation.Type, Conversation.ProtocolInfo>>
}

internal class ConversationMetaDataDataSource internal  constructor(
    private val conversationMetaDataDAO: ConversationMetaDataDAO,
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
) : ConversationMetaDataRepository {


    override suspend fun getConversationTypeAndProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Pair<Conversation.Type, Conversation.ProtocolInfo>> =
        wrapStorageRequest {
            conversationMetaDataDAO.typeAndProtocolInfo(conversationId.toDao())?.let {
                Pair(
                    it.type.fromDaoModelToType(it.isChannel),
                    protocolInfoMapper.fromEntity(it.protocolInfo)
                )
            }
        }

}
