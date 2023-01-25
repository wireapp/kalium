/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.call.ConversationType
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
        conversationType: ConversationType,
        isAudioCbr: Boolean
    ) // TODO(calling): Audio CBR

    suspend fun answerCall(conversationId: ConversationId)
    suspend fun endCall(conversationId: ConversationId)
    suspend fun rejectCall(conversationId: ConversationId)
    suspend fun muteCall(shouldMute: Boolean)
    suspend fun updateVideoState(conversationId: ConversationId, videoState: VideoState)
    suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList)
}
