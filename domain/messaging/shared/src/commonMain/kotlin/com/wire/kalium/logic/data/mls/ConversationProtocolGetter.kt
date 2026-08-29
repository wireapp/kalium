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
package com.wire.kalium.logic.data.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ProtocolInfoMapper
import com.wire.kalium.logic.data.conversation.ProtocolInfoMapperImpl
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun interface ConversationProtocolGetter {
    public suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<CoreFailure, Conversation.ProtocolInfo>
}

@InternalKaliumApi
public class ConversationProtocolGetterImpl private constructor(
    private val conversationDAO: ConversationDAO,
    private val protocolInfoMapper: ProtocolInfoMapper
) : ConversationProtocolGetter {
    public constructor(conversationDAO: ConversationDAO) : this(conversationDAO, ProtocolInfoMapperImpl())

    override suspend fun getConversationProtocolInfo(
        conversationId: ConversationId
    ): Either<CoreFailure, Conversation.ProtocolInfo> = wrapStorageRequest {
        conversationDAO.getConversationProtocolInfo(conversationId.toDao())
    }.map { protocolInfoMapper.fromEntity(it) }
}
