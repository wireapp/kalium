package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
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
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val selfUserId: String,
    private val selfClientId: String,
    private val messageSender: MessageSender,
    private val callingScope: CoroutineScope
) : SendHandler {
    override fun onSend(
        context: Pointer?,
        conversationIdString: String,
        userIdSelfString: String,
        clientIdSelfString: String,
        userIdDestination: String?,
        clientIdDestination: String?,
        data: Pointer?,
        length: Size_t,
        isTransient: Boolean,
        arg: Pointer?
    ): Int {
        callingLogger.i("[OnSendOTR] -> ConversationId: $conversationIdString")
        return if (selfUserId != userIdSelfString && selfClientId != clientIdSelfString) {
            callingLogger.i("[OnSendOTR] -> selfUserId: $selfUserId != userIdSelf: $userIdSelfString")
            callingLogger.i("[OnSendOTR] -> selfClientId: $selfClientId != clientIdSelf: $clientIdSelfString")
            AvsCallBackError.INVALID_ARGUMENT.value
        } else {
            callingLogger.i("[OnSendOTR] -> Success")
            OnHttpRequest(handle, calling, messageSender, callingScope).sendHandlerSuccess(
                context = context,
                messageString = data?.getString(0, CallManagerImpl.UTF8_ENCODING),
                conversationId = qualifiedIdMapper.fromStringToQualifiedID(conversationIdString),
                avsSelfUserId = clientIdSelfString.toUserId(),
                avsSelfClientId = ClientId(clientIdSelfString)
            )
            AvsCallBackError.NONE.value
        }
    }
}
