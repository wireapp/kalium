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

package com.wire.kalium.network.api.v9.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.conversation.ResetMLSConversationRequestV9
import com.wire.kalium.network.api.v8.authenticated.ConversationApiV8
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class ConversationApiV9 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApiV8(authenticatedNetworkClient) {

    override suspend fun resetMlsConversation(groupId: String, epoch: ULong): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post("$PATH_MLS/$PATH_RESET_CONVERSATION") {
            setBody(
                ResetMLSConversationRequestV9(
                    epoch = epoch,
                    groupId = groupId
                )
            )
        }
    }

    private companion object {
        const val PATH_MLS = "mls"
        private const val PATH_RESET_CONVERSATION = "reset-conversation"
    }
}
