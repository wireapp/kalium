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

interface CallManager {
    suspend fun onCallingMessageReceived(
        message: Message.Signaling,
        content: MessageContent.Calling,
    )
    suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        isAudioCbr: Boolean
    )

    suspend fun answerCall(conversationId: ConversationId, isAudioCbr: Boolean)
    suspend fun endCall(conversationId: ConversationId)
    suspend fun rejectCall(conversationId: ConversationId)
    suspend fun muteCall(shouldMute: Boolean)
    suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState)
    suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList)
    suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo)
    suspend fun updateConversationClients(conversationId: ConversationId, clients: String)
    suspend fun reportProcessNotifications(isStarted: Boolean)
    suspend fun cancelJobs()
}
