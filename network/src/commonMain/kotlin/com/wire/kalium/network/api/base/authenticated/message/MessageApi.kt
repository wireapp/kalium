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

package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.network.api.authenticated.message.Parameters
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable

@Mockable
interface MessageApi {

    suspend fun qualifiedSendMessage(
        parameters: Parameters.QualifiedDefaultParameters,
        conversationId: ConversationId
    ): NetworkResponse<QualifiedSendMessageResponse>

    suspend fun qualifiedBroadcastMessage(
        parameters: Parameters.QualifiedDefaultParameters
    ): NetworkResponse<QualifiedSendMessageResponse>
}
