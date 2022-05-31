package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.ClientsRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.user.toUserId
import com.wire.kalium.logic.feature.message.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

class OnClientsRequest(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val selfUserId: String,
    private val selfClientId: String,
    private val messageSender: MessageSender,
    private val callingScope: CoroutineScope
) : ClientsRequestHandler {

    override fun onClientsRequest(inst: Handle, conversationId: String, arg: Pointer?) {
        OnHttpRequest(handle, calling, messageSender, callingScope).sendClientDiscoveryMessage(
            conversationId = conversationId.toConversationId(),
            userId = selfUserId.toUserId(),
            clientId = ClientId(selfClientId)
        )
    }
}
