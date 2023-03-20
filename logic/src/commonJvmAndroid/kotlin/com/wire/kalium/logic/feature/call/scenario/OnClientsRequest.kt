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

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.ClientsRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OnClientsRequest(
    private val calling: Calling,
    private val conversationRepository: ConversationRepository,
    private val federatedIdMapper: FederatedIdMapper,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val callingScope: CoroutineScope
) : ClientsRequestHandler {

    override fun onClientsRequest(inst: Handle, conversationId: String, arg: Pointer?) {
        callingScope.launch {
            callingLogger.d("[OnClientsRequest] -> ConversationId: $conversationId")
            val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
            val conversationRecipients = conversationRepository.getConversationRecipientsForCalling(
                conversationId = conversationIdWithDomain
            )

            conversationRecipients.map { recipients ->
                callingLogger.d("[OnClientsRequest] -> Mapping ${recipients.size} recipients")
                recipients
                    .flatMap { recipient ->
                        recipient.clients.map { clientId ->
                            CallClient(
                                userId = federatedIdMapper.parseToFederatedId(recipient.id),
                                clientId = clientId.value
                            )
                        }
                    }
            }.map {
                CallClientList(it)
            }.onSuccess { avsClients ->
                callingLogger.d("[OnClientsRequest] -> Sending recipients")
                // TODO: use a json serializer and not recreate everytime it is used
                val callClients = avsClients.toJsonString()
                calling.wcall_set_clients_for_conv(
                    inst = inst,
                    convId = federatedIdMapper.parseToFederatedId(conversationId),
                    clientsJson = callClients
                )
                callingLogger.d("[OnClientsRequest] -> Success")
            }.onFailure {
                callingLogger.d("[OnClientsRequest] -> Failure")
            }
        }
    }
}
