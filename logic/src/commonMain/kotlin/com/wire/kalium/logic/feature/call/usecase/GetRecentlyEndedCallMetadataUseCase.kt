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
import com.wire.kalium.logic.data.call.RecentlyEndedCallMetadata
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
interface GetRecentlyEndedCallMetadataUseCase {
    suspend operator fun invoke(call: Call, callEndedReason: Int): RecentlyEndedCallMetadata
}

class GetRecentlyEndedCallMetadataUseCaseImpl internal constructor(
    private val observeConversationMembers: ObserveConversationMembersUseCase,
    private val getSelf: GetSelfUserUseCase,
) : GetRecentlyEndedCallMetadataUseCase {
    override suspend fun invoke(call: Call, callEndedReason: Int): RecentlyEndedCallMetadata {
        val selfUser = getSelf().firstOrNull()
        val selfCallUser = call.participants.firstOrNull { participant -> participant.userType == UserType.OWNER }
        val conversationMembers = observeConversationMembers(call.conversationId).first()
        val conversationServicesCount = conversationMembers.count { member -> member.user.userType == UserType.SERVICE }
        val guestsCount = conversationMembers.count { member -> member.user.userType == UserType.GUEST }
        val guestsProCount = conversationMembers.count { member -> member.user.userType == UserType.GUEST && member.user.teamId != null }
        val uniqueScreenShares = call.participants.count { participant -> participant.isSharingScreen }
        val isOutgoingCall = call.callerId.value == selfCallUser?.id?.value
        val callDurationInSeconds = call.establishedTime?.let {
            DateTimeUtil.calculateMillisDifference(it, DateTimeUtil.currentIsoDateTimeString()) / MILLIS_IN_SECOND
        } ?: 0L

        return RecentlyEndedCallMetadata(
            callEndReason = callEndedReason,
            callDetails = RecentlyEndedCallMetadata.CallDetails(
                isCallScreenShare = selfCallUser?.isSharingScreen ?: false,
                callScreenShareUniques = uniqueScreenShares,
                isOutgoingCall = isOutgoingCall,
                callDurationInSeconds = callDurationInSeconds,
                callParticipantsCount = call.participants.size,
                conversationServices = conversationServicesCount,
                callAVSwitchToggle = selfCallUser?.isCameraOn ?: false,
                callVideoEnabled = call.isCameraOn
            ),
            conversationDetails = RecentlyEndedCallMetadata.ConversationDetails(
                conversationType = call.conversationType,
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
