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
package com.wire.kalium.network.api.base.authenticated.conversation.history

import com.wire.kalium.network.api.authenticated.conversation.ConversationHistoryResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationHistorySettingsDTO
import com.wire.kalium.network.api.authenticated.conversation.HistoryClientId
import com.wire.kalium.network.api.base.authenticated.BaseApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable

@Mockable
interface ConversationHistoryApi : BaseApi {
    suspend fun updateHistorySettingsForConversation(
        conversationId: ConversationId,
        settings: ConversationHistorySettingsDTO,
    ): NetworkResponse<Unit>

    /**
     * Retrieves messages from a conversation for a given history client, in a paginated fashion.
     * @param offset The number of messages to skip in this request.
     * @param size The size of a page. Must be between 1 and 1000. Defaults to 100.
     */
    suspend fun getPageOfMessagesForHistoryClient(
        conversationId: ConversationId,
        historyClientId: HistoryClientId,
        offset: ULong,
        size: UInt = 100u,
    ): NetworkResponse<ConversationHistoryResponse>
}
