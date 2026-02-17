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
import com.wire.kalium.common.logger.kaliumLogger

@Suppress("TooManyFunctions")
internal class CallManagerImpl : CallManager {
    override suspend fun onCallingMessageReceived(message: Message.Signaling, content: MessageContent.Calling) {
        kaliumLogger.w("Ignoring call message since calling is not supported")
    }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationTypeCalling: ConversationTypeCalling,
        isAudioCbr: Boolean
    ) {
        kaliumLogger.w("Calls not supported on iOS: startCall ignored")
    }

    override suspend fun answerCall(conversationId: ConversationId, isAudioCbr: Boolean, isVideoCall: Boolean) {
        kaliumLogger.w("Calls not supported on iOS: answerCall ignored")
    }

    override suspend fun endCall(conversationId: ConversationId) {
        kaliumLogger.w("Calls not supported on iOS: endCall ignored")
    }

    override suspend fun rejectCall(conversationId: ConversationId) {
        kaliumLogger.w("Calls not supported on iOS: rejectCall ignored")
    }

    override suspend fun muteCall(shouldMute: Boolean) {
        kaliumLogger.w("Calls not supported on iOS: muteCall ignored")
    }

    override suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState) {
        kaliumLogger.w("Calls not supported on iOS: setVideoSendState ignored")
    }

    override suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList) {
        kaliumLogger.w("Calls not supported on iOS: requestVideoStreams ignored")
    }

    override suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo) {
        kaliumLogger.w("Calls not supported on iOS: updateEpochInfo ignored")
    }

    override suspend fun updateConversationClients(conversationId: ConversationId, clients: String) {
        kaliumLogger.w("Calls not supported on iOS: updateConversationClients ignored")
    }

    override suspend fun reportProcessNotifications(isStarted: Boolean) {
        kaliumLogger.w("Calls not supported on iOS: reportProcessNotifications ignored")
    }

    override suspend fun setTestVideoType(testVideoType: TestVideoType) {
        kaliumLogger.w("Calls not supported on iOS: setTestVideoType ignored")
    }

    override suspend fun setTestPreviewActive(shouldEnable: Boolean) {
        kaliumLogger.w("Calls not supported on iOS: setTestPreviewActive ignored")
    }

    override suspend fun setTestRemoteVideoStates(conversationId: ConversationId, participants: List<Participant>) {
        kaliumLogger.w("Calls not supported on iOS: setTestRemoteVideoStates ignored")
    }

    override suspend fun setBackground(background: Boolean) {
        kaliumLogger.w("Calls not supported on iOS: setBackground ignored")
    }

    override suspend fun cancelJobs() {
        kaliumLogger.w("Calls not supported on iOS: cancelJobs ignored")
    }
}
