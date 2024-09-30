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
package com.wire.kalium.logic.util

import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.TestVideoType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.call.CallManager

@Suppress("EmptyFunctionBlock", "TooManyFunctions")
class DummyCallManager : CallManager {
    override suspend fun onCallingMessageReceived(message: Message.Signaling, content: MessageContent.Calling) {}

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationTypeCalling: ConversationTypeCalling,
        isAudioCbr: Boolean
    ) {
    }

    override suspend fun answerCall(conversationId: ConversationId, isAudioCbr: Boolean) {}

    override suspend fun endCall(conversationId: ConversationId) {}

    override suspend fun rejectCall(conversationId: ConversationId) {}

    override suspend fun muteCall(shouldMute: Boolean) {}

    override suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState) {}

    override suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList) {}

    override suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo) {}

    override suspend fun updateConversationClients(conversationId: ConversationId, clients: String) {}

    override suspend fun reportProcessNotifications(isStarted: Boolean) {}

    override suspend fun setTestVideoType(testVideoType: TestVideoType) {}

    override suspend fun setTestPreviewActive(shouldEnable: Boolean) {}

    override suspend fun setTestRemoteVideoStates(conversationId: ConversationId, participants: List<Participant>) {}

    override suspend fun cancelJobs() {}
}
