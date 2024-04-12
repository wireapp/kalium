/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.EpochInfo
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
        isAudioCbr: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun answerCall(conversationId: ConversationId, isAudioCbr: Boolean) {
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

    override suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState) {
        TODO("Not yet implemented")
    }

    override suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList) {
        TODO("Not yet implemented")
    }

    override suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo) {
        TODO("Not yet implemented")
    }

    override suspend fun updateConversationClients(conversationId: ConversationId, clients: String) {
        TODO("Not yet implemented")
    }

    override suspend fun reportProcessNotifications(isStarted: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun cancelJobs() {
        TODO("Not yet implemented")
    }
}
