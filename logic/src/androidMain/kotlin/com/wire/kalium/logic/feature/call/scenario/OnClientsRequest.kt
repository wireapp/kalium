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
    private val selfUserId: String,
    private val conversationRepository: ConversationRepository,
    private val federatedIdMapper: FederatedIdMapper,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val callingScope: CoroutineScope
) : ClientsRequestHandler {

    override fun onClientsRequest(inst: Handle, conversationIdString: String, arg: Pointer?) {
        callingScope.launch {
            callingLogger.d("[OnClientsRequest] -> ConversationId: $conversationIdString")
            val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationIdString)
            val conversationRecipients = conversationRepository.getConversationRecipients(
                conversationId = conversationIdWithDomain
            )

            conversationRecipients.map { recipients ->
                callingLogger.d("[OnClientsRequest] -> Mapping ${recipients.size} recipients")
                recipients
                    .filter { it.id.value != selfUserId }
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
                    convId = federatedIdMapper.parseToFederatedId(conversationIdString),
                    clientsJson = callClients
                )
                callingLogger.d("[OnClientsRequest] -> Success")
            }.onFailure {
                callingLogger.d("[OnClientsRequest] -> Failure")
            }
        }
    }
}
