package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.kaliumLogger

actual class CallManagerImpl : CallManager {

    init {
        kaliumLogger.w("CallManager initialized for JVM but not supported yet.")
    }

    override suspend fun onCallingMessageReceived(message: Message.Client, content: MessageContent.Calling) {
        kaliumLogger.w("onCallingMessageReceived for JVM but not supported yet.")
    }

    override suspend fun startCall(conversationId: ConversationId, callType: CallType, conversationType: ConversationType, isAudioCbr: Boolean) {
        kaliumLogger.w("startCall for JVM but no supported yet.")
    }

    override suspend fun answerCall(conversationId: ConversationId) {
        kaliumLogger.w("answerCall for JVM but not supported yet.")
    }

    override suspend fun endCall(conversationId: ConversationId) {
        kaliumLogger.w("endCall for JVM but not supported yet.")
    }

    override suspend fun rejectCall(conversationId: ConversationId) {
        kaliumLogger.w("rejectCall for JVM but not supported yet.")
    }

    override suspend fun muteCall(shouldMute: Boolean) {
        kaliumLogger.w("muteCall for JVM but not supported yet.")
    }

    override suspend fun updateVideoState(conversationId: ConversationId, videoState: VideoState) {
        kaliumLogger.w("updateVideoState for JVM but not supported yet.")
    }
}
