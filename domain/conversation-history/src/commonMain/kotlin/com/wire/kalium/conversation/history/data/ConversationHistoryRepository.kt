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
package com.wire.kalium.conversation.history.data

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.conversation.history.data.dao.ConversationHistoryDAO
import com.wire.kalium.logic.data.conversation.ConversationHistorySettings
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.base.authenticated.conversation.history.ConversationHistoryApi

public interface ConversationHistoryRepository {
    public suspend fun updateSettingsForConversation(
        conversationId: ConversationId,
        historySettings: ConversationHistorySettings
    ): Either<CoreFailure, Unit>
}

public fun ConversationHistoryRepository(
    historyApi: ConversationHistoryApi,
    conversationHistoryDAO: ConversationHistoryDAO,
    historyMapper: ConversationHistoryMapper = ConversationHistoryMapper(),
    idMapper: IdMapper = IdMapper(),
): ConversationHistoryRepository = object : ConversationHistoryRepository {
    override suspend fun updateSettingsForConversation(
        conversationId: ConversationId,
        historySettings: ConversationHistorySettings
    ) = wrapApiRequest {
        historyApi.updateHistorySettingsForConversation(
            conversationId = idMapper.toNetworkUserId(conversationId),
            settings = historyMapper.fromDomainToApi(historySettings)
        )
    }.flatMap {
        wrapStorageRequest {
            val historyRetentionInSeconds = when (historySettings) {
                ConversationHistorySettings.Private -> 0UL
                is ConversationHistorySettings.ShareWithNewMembers -> historySettings.retention.inWholeSeconds.toULong()
            }
            conversationHistoryDAO.updateConversationHistorySettings(
                conversationId = idMapper.fromDomainToDao(conversationId),
                historyRetentionInSeconds = historyRetentionInSeconds
            )
        }
    }
}
