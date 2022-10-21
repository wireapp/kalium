package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.MessageTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// TODO(testing): create unit test
@Suppress("LongParameterList")
class OnSendOTR(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val selfUserId: String,
    private val selfClientId: String,
    private val messageSender: MessageSender,
    private val callingScope: CoroutineScope,
    private val callMapper: CallMapper
) : SendHandler {
    @Suppress("TooGenericExceptionCaught")
    override fun onSend(
        context: Pointer?,
        remoteConversationIdString: String,
        remoteUserIdSelfString: String,
        remoteClientIdSelfString: String,
        targetRecipientsJson: String?,
        clientIdDestination: String?,
        data: Pointer?,
        length: Size_t,
        isTransient: Boolean,
        arg: Pointer?
    ): Int {
        callingLogger.i("[OnSendOTR] -> ConversationId: $remoteConversationIdString")
        return if (selfUserId != remoteUserIdSelfString && selfClientId != remoteClientIdSelfString) {
            callingLogger.i("[OnSendOTR] -> selfUserId: $selfUserId != userIdSelf: $remoteUserIdSelfString")
            callingLogger.i("[OnSendOTR] -> selfClientId: $selfClientId != clientIdSelf: $remoteClientIdSelfString")
            AvsCallBackError.INVALID_ARGUMENT.value
        } else {
            try {
                callingLogger.i("[OnSendOTR] -> Decoding Recipients")
                val messageTarget = targetRecipientsJson?.let { recipientsJson ->
                    val callClientList = Json.decodeFromString<CallClientList>(recipientsJson)

                    callingLogger.i("[OnSendOTR] -> Mapping Recipients")
                    callMapper.toClientMessageTarget(callClientList = callClientList)
                } ?: MessageTarget.Conversation

                callingLogger.i("[OnSendOTR] -> Success")
                OnHttpRequest(handle, calling, messageSender, callingScope).sendHandlerSuccess(
                    context = context,
                    messageString = data?.getString(0, CallManagerImpl.UTF8_ENCODING),
                    conversationId = qualifiedIdMapper.fromStringToQualifiedID(remoteConversationIdString),
                    avsSelfUserId = qualifiedIdMapper.fromStringToQualifiedID(remoteUserIdSelfString),
                    avsSelfClientId = ClientId(remoteClientIdSelfString),
                    messageTarget = messageTarget
                )
                AvsCallBackError.NONE.value
            } catch (exception: Exception) {
                callingLogger.e("[OnSendOTR] -> Error Exception: $exception")
                AvsCallBackError.COULD_NOT_DECODE_ARGUMENT.value
            }
        }
    }
}
