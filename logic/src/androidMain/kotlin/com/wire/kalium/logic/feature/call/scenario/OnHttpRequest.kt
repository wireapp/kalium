package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.call.SendCallingMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//TODO create unit test
class OnHttpRequest(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val sendCallingMessage: SendCallingMessage
) {
    fun sendHandlerSuccess(
        context: Pointer?,
        messageString: String?,
        conversationId: ConversationId,
        avsSelfUserId: UserId,
        avsSelfClientId: ClientId
    ) {
        //TODO use the same coroutine job of call manager
        CoroutineScope(Dispatchers.IO).launch {
            messageString?.let { message ->
                when (sendCallingMessage.sendMessage(conversationId, avsSelfUserId, avsSelfClientId, message)) {
                    is Either.Right -> {
                        calling.wcall_resp(
                            inst = handle.await(),
                            status = 200,
                            reason = "",
                            arg = context
                        )
                    }
                    is Either.Left -> {
                        calling.wcall_resp(
                            inst = handle.await(),
                            status = 400, // TODO: Handle the errorCode from CoreFailure
                            reason = "Couldn't send Calling Message",
                            arg = context
                        )
                    }
                }
            }
        }
    }
}
