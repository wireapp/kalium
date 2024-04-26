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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for requesting video streams for a call to avs.
 */
class RequestVideoStreamsUseCase(
    private val callManager: Lazy<CallManager>,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @param conversationId the id of the conversation.
     * @param clients the list of clients that should be requested for video streams.
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        clients: List<CallClient>
    ) = withContext(dispatchers.io) {
        callingLogger.i("Requesting video streams for conversationId: ${conversationId.toLogString()}")
        val callClients = CallClientList(clients = clients)
        callManager.value.requestVideoStreams(conversationId, callClients)
    }
}
