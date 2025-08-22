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

package com.wire.kalium.network.api.v11.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.conversation.ConversationHistoryResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationHistorySettingsDTO
import com.wire.kalium.network.api.authenticated.conversation.HistoryClientId
import com.wire.kalium.network.api.base.authenticated.conversation.history.ConversationHistoryApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.appendPathSegments

internal open class ConversationHistoryApiV11 internal constructor(private val networkClient: AuthenticatedNetworkClient) :
    ConversationHistoryApi {

    private val httpClient get() = networkClient.httpClient
    override suspend fun updateHistorySettingsForConversation(
        conversationId: ConversationId,
        settings: ConversationHistorySettingsDTO
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put {
            url {
                appendPathSegments(CONVERSATIONS_PATH, conversationId.domain, conversationId.value, HISTORY_PATH)
            }
            setBody(settings)
        }
    }

    override suspend fun getPageOfMessagesForHistoryClient(
        conversationId: ConversationId,
        historyClientId: HistoryClientId,
        offset: ULong,
        size: UInt
    ): NetworkResponse<ConversationHistoryResponse> = wrapKaliumResponse {
        httpClient.get {
            url {
                appendPathSegments(HISTORY_PATH, conversationId.domain, conversationId.value, historyClientId.value)
                parameters.append("offset", offset.toString())
                parameters.append("size", size.toString())
            }
        }
    }

    protected companion object {
        const val CONVERSATIONS_PATH = "conversations"
        const val HISTORY_PATH = "history"
    }
}
