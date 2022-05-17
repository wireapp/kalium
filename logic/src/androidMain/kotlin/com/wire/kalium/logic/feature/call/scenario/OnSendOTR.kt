package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.user.toUserId
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.message.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

//TODO(testing): create unit test
class OnSendOTR(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val selfUserId: String,
    private val selfClientId: String,
    private val messageSender: MessageSender,
    private val callingScope: CoroutineScope
) : SendHandler {
    override fun onSend(
        context: Pointer?,
        conversationId: String,
        userIdSelf: String,
        clientIdSelf: String,
        userIdDestination: String?,
        clientIdDestination: String?,
        data: Pointer?,
        length: Size_t,
        isTransient: Boolean,
        arg: Pointer?
    ): Int {
        return if (selfUserId != userIdSelf && selfClientId != clientIdSelf) {
            callingLogger.i("OnSendOTR -> sendHandler error called")
            AvsCallBackError.INVALID_ARGUMENT.value
        } else {
            callingLogger.i("OnSendOTR -> sendHandler success called")
            OnHttpRequest(handle, calling, messageSender, callingScope).sendHandlerSuccess(
                context = context,
                messageString = data?.getString(0, CallManagerImpl.UTF8_ENCODING),
                conversationId = conversationId.toConversationId(),
                avsSelfUserId = userIdSelf.toUserId(),
                avsSelfClientId = ClientId(clientIdSelf)
            )
            AvsCallBackError.NONE.value
        }
    }
}
