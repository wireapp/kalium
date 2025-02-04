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

package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.OtherUserMinimized
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import kotlinx.datetime.Instant

data class CallMetadataProfile(
    val data: Map<ConversationId, CallMetadata>
) {
    operator fun get(conversationId: ConversationId): CallMetadata? = data[conversationId]
}

data class CallMetadata(
    val callerId: QualifiedID,
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val isCbrEnabled: Boolean,
    val conversationName: String?,
    val conversationType: Conversation.Type,
    val callerName: String?,
    val callerTeamName: String?,
    val establishedTime: String? = null,
    val callStatus: CallStatus,
    val participants: List<ParticipantMinimized> = emptyList(),
    val maxParticipants: Int = 0, // Was used for tracking
    val protocol: Conversation.ProtocolInfo,
    val activeSpeakers: Map<UserId, List<String>> = mapOf(),
    val users: List<OtherUserMinimized> = listOf(),
    val screenShareMetadata: CallScreenSharingMetadata = CallScreenSharingMetadata()
) {
    fun getFullParticipants(): List<Participant> = participants.map { participant ->
        val user = users.firstOrNull { it.id == participant.userId }
        val isSpeaking = (activeSpeakers[participant.userId]?.contains(participant.clientId) ?: false) && !participant.isMuted
        Participant(
            id = participant.id,
            clientId = participant.clientId,
            name = user?.name,
            isMuted = participant.isMuted,
            isCameraOn = participant.isCameraOn,
            isSpeaking = isSpeaking,
            isSharingScreen = participant.isSharingScreen,
            hasEstablishedAudio = participant.hasEstablishedAudio,
            avatarAssetId = user?.completePicture,
            userType = user?.userType ?: UserType.NONE,
            accentId = user?.accentId ?: 0
        )
    }
}

/**
 * [activeScreenShares] - map of user ids that share screen with the start timestamp
 * [completedScreenShareDurationInMillis] - total time of already ended screen shares in milliseconds
 * [uniqueSharingUsers] - set of users that were sharing a screen at least once
 */
data class CallScreenSharingMetadata(
    val activeScreenShares: Map<QualifiedID, Instant> = emptyMap(),
    val completedScreenShareDurationInMillis: Long = 0L,
    val uniqueSharingUsers: Set<String> = emptySet()
)
