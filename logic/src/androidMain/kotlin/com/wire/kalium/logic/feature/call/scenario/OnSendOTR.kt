package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.SendCallingMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.user.toUserId
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import kotlinx.coroutines.Deferred

//TODO create unit test
class OnSendOTR(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val selfUserId: String,
    private val selfClientId: String,
    private val sendCallingMessage: SendCallingMessage
) : SendHandler {
    override fun onSend(
        context: Pointer?,
        conversationId: String,
        avsSelfUserId: String,
        avsSelfClientId: String,
        userIdDestination: String?,
        clientIdDestination: String?,
        data: Pointer?,
        length: Size_t,
        isTransient: Boolean,
        arg: Pointer?
    ): Int {
        return if (selfUserId != avsSelfUserId && selfClientId != avsSelfClientId) {
            callingLogger.i("${CallManagerImpl.TAG} -> sendHandler error")
            AvsCallBackError.INVALID_ARGUMENT.value
        } else {
            callingLogger.i("${CallManagerImpl.TAG} -> sendHandler success")
            OnHttpRequest(handle, calling, sendCallingMessage).sendHandlerSuccess(
                context = context,
                messageString = data?.getString(0, CallManagerImpl.UTF8_ENCODING),
                conversationId = conversationId.toConversationId(),
                avsSelfUserId = avsSelfUserId.toUserId(),
                avsSelfClientId = ClientId(avsSelfClientId)
            )
            AvsCallBackError.NONE.value
        }
    }
}
