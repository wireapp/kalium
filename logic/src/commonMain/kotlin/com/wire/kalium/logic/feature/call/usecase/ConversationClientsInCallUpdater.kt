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
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This class is responsible for updating conversation clients in a call
 * Usually called when a member is removed from conversation
 */
interface ConversationClientsInCallUpdater {
    suspend operator fun invoke(conversationId: ConversationId)
}

class ConversationClientsInCallUpdaterImpl(
    private val callManager: Lazy<CallManager>,
    private val conversationRepository: ConversationRepository,
    private val federatedIdMapper: FederatedIdMapper,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ConversationClientsInCallUpdater {
    override suspend fun invoke(conversationId: ConversationId) {
        conversationRepository.getConversationRecipientsForCalling(
            conversationId = conversationId
        ).map {
            it.flatMap { recipient ->
                recipient.clients.map { clientId ->
                    CallClient(
                        userId = federatedIdMapper.parseToFederatedId(recipient.id),
                        clientId = clientId.value
                    )
                }
            }
        }.map {
            CallClientList(it)
        }.onSuccess {
            callingLogger.d("[ConversationClientsInCallUpdater] -> Sending recipients $it")
            val callClients = it.toJsonString()
            withContext(dispatchers.default) {
                callManager.value.updateConversationClients(conversationId, callClients)
            }
            callingLogger.d("[ConversationClientsInCallUpdater] -> recipients sent")
        }.onFailure {
            callingLogger.d("[ConversationClientsInCallUpdater] -> failed tp send recipients")
        }
    }

}
