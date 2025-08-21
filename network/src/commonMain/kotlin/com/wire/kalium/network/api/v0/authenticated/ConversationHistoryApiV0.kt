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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.api.authenticated.conversation.ConversationHistoryResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationHistorySettingsDTO
import com.wire.kalium.network.api.authenticated.conversation.HistoryClientId
import com.wire.kalium.network.api.base.authenticated.conversation.history.ConversationHistoryApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.utils.NetworkResponse

internal open class ConversationHistoryApiV0 internal constructor() : ConversationHistoryApi {
    @Suppress("MagicNumber")
    override suspend fun updateHistorySettingsForConversation(
        conversationId: ConversationId,
        settings: ConversationHistorySettingsDTO
    ): NetworkResponse<Unit> = getApiNotSupportedError(::updateHistorySettingsForConversation.name, 11)

    @Suppress("MagicNumber")
    override suspend fun getPageOfMessagesForHistoryClient(
        conversationId: ConversationId,
        historyClientId: HistoryClientId,
        offset: ULong,
        size: UInt
    ): NetworkResponse<ConversationHistoryResponse> = getApiNotSupportedError(::getPageOfMessagesForHistoryClient.name, 11)
}
