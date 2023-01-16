package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.kaliumLogger

class CallManagerImpl : CallManager {
    override suspend fun onCallingMessageReceived(message: Message.Signaling, content: MessageContent.Calling) {
        kaliumLogger.w("Ignoring call message since calling is not supported")
    }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationType: ConversationType,
        isAudioCbr: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun answerCall(conversationId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun endCall(conversationId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun rejectCall(conversationId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun muteCall(shouldMute: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun updateVideoState(conversationId: ConversationId, videoState: VideoState) {
        TODO("Not yet implemented")
    }

    override suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList) {
        TODO("Not yet implemented")
    }
}
