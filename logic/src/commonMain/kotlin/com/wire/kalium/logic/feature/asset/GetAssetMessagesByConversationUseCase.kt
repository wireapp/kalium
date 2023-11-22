/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetMessage
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext

/**
 * This use case will observe and return a flow of paginated asset messages for a given conversation.
 * // TODO KBX
 * @see Message
 */
class GetAssetMessagesByConversationUseCase internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val messageRepository: MessageRepository,
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        limit: Int,
        offset: Int,
    ): List<AssetMessage> = withContext(dispatcher.io) {
        messageRepository.getAssetMessagesByConversationId(
            conversationId, limit, offset
        )
    }
}
