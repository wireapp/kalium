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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.RecentlyEndedCallMetadata
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.conversation.ObserveConversationMembersUseCase
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * Given a call and raw call end reason create metadata containing all information regarding
 * a call.
 */
interface CreateAndPersistRecentlyEndedCallMetadataUseCase {
    suspend operator fun invoke(conversationId: ConversationId, callEndedReason: Int)
}

class CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val observeConversationMembers: ObserveConversationMembersUseCase,
    private val getSelf: GetSelfUserUseCase,
) : CreateAndPersistRecentlyEndedCallMetadataUseCase {
    override suspend fun invoke(conversationId: ConversationId, callEndedReason: Int) {
        val call = callRepository.observeCurrentCall(conversationId).first()
        call?.createMetadata(callEndedReason = callEndedReason)?.let { metadata ->
            callRepository.updateRecentlyEndedCallMetadata(metadata)
        }
    }

    private suspend fun Call.createMetadata(callEndedReason: Int): RecentlyEndedCallMetadata {
        val selfUser = getSelf().firstOrNull()
        val selfCallUser = participants.firstOrNull { participant -> participant.userType == UserType.OWNER }
        val conversationMembers = observeConversationMembers(conversationId).first()
        val conversationServicesCount = conversationMembers.count { member -> member.user.userType == UserType.SERVICE }
        val guestsCount = conversationMembers.count { member -> member.user.userType == UserType.GUEST }
        val guestsProCount = conversationMembers.count { member -> member.user.userType == UserType.GUEST && member.user.teamId != null }
        val uniqueScreenShares = participants.count { participant -> participant.isSharingScreen }
        val isOutgoingCall = callerId.value == selfCallUser?.id?.value
        val callDurationInSeconds = establishedTime?.let {
            DateTimeUtil.calculateMillisDifference(it, DateTimeUtil.currentIsoDateTimeString()) / MILLIS_IN_SECOND
        } ?: 0L

        return RecentlyEndedCallMetadata(
            callEndReason = callEndedReason,
            callDetails = RecentlyEndedCallMetadata.CallDetails(
                isCallScreenShare = selfCallUser?.isSharingScreen ?: false,
                callScreenShareUniques = uniqueScreenShares,
                isOutgoingCall = isOutgoingCall,
                callDurationInSeconds = callDurationInSeconds,
                callParticipantsCount = participants.size,
                conversationServices = conversationServicesCount,
                callAVSwitchToggle = selfCallUser?.isCameraOn ?: false,
                callVideoEnabled = isCameraOn
            ),
            conversationDetails = RecentlyEndedCallMetadata.ConversationDetails(
                conversationType = conversationType,
                conversationSize = conversationMembers.size,
                conversationGuests = guestsCount,
                conversationGuestsPro = guestsProCount
            ),
            isTeamMember = selfUser?.teamId != null
        )
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
    }
}
