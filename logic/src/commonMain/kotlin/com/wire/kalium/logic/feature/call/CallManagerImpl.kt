package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent

interface CallManager {
    suspend fun onCallingMessageReceived(message: Message.Regular, content: MessageContent.Calling)
    suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationType: ConversationType,
        isAudioCbr: Boolean
    ) //TODO(calling): Audio CBR

    suspend fun answerCall(conversationId: ConversationId)
    suspend fun endCall(conversationId: ConversationId)
    suspend fun rejectCall(conversationId: ConversationId)
    suspend fun muteCall(shouldMute: Boolean)
    suspend fun updateVideoState(conversationId: ConversationId, videoState: VideoState)
}

expect class CallManagerImpl : CallManager
