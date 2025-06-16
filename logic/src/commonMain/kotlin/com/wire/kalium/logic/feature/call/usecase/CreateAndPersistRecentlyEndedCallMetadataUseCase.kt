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

import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallScreenSharingMetadata
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.RecentlyEndedCallMetadata
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.conversation.ObserveConversationMembersUseCase
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mockable
import kotlinx.coroutines.flow.first

/**
 * Given a call and raw call end reason create metadata containing all information regarding
 * a call.
 */
@Mockable
interface CreateAndPersistRecentlyEndedCallMetadataUseCase {
    suspend operator fun invoke(conversationId: ConversationId, callEndedReason: Int)
}

class CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val observeConversationMembers: ObserveConversationMembersUseCase,
    private val selfTeamIdProvider: SelfTeamIdProvider,
) : CreateAndPersistRecentlyEndedCallMetadataUseCase {
    override suspend fun invoke(conversationId: ConversationId, callEndedReason: Int) {
        callRepository.getCallMetadataProfile()[conversationId]?.createMetadata(
            conversationId = conversationId,
            callEndedReason = callEndedReason
        )?.let { metadata ->
            callRepository.updateRecentlyEndedCallMetadata(metadata)
        }
    }

    private suspend fun CallMetadata.createMetadata(conversationId: ConversationId, callEndedReason: Int): RecentlyEndedCallMetadata {
        val selfCallUser = getFullParticipants().firstOrNull { participant -> participant.userType == UserType.OWNER }
        val conversationMembers = observeConversationMembers(conversationId).first()
        val conversationServicesCount = conversationMembers.count { member -> member.user.userType == UserType.SERVICE }
        val guestsCount = conversationMembers.count { member -> member.user.userType == UserType.GUEST }
        val guestsProCount = conversationMembers.count { member -> member.user.userType == UserType.GUEST && member.user.teamId != null }
        val isOutgoingCall = callStatus == CallStatus.STARTED
        val callDurationInSeconds = establishedTime?.let {
            DateTimeUtil.calculateMillisDifference(it, DateTimeUtil.currentIsoDateTimeString()) / MILLIS_IN_SECOND
        } ?: 0L

        return RecentlyEndedCallMetadata(
            callEndReason = callEndedReason,
            callDetails = RecentlyEndedCallMetadata.CallDetails(
                isCallScreenShare = selfCallUser?.isSharingScreen ?: false,
                screenShareDurationInSeconds = screenShareMetadata.totalDurationInSeconds(),
                callScreenShareUniques = screenShareMetadata.uniqueSharingUsers.size,
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
            isTeamMember = selfTeamIdProvider().getOrNull() != null
        )
    }

    private fun CallScreenSharingMetadata.totalDurationInSeconds(): Long {
        val now = DateTimeUtil.currentInstant()
        val activeScreenSharesDurationInSeconds =
            activeScreenShares.values.sumOf { startTime -> DateTimeUtil.calculateMillisDifference(startTime, now) }

        return (activeScreenSharesDurationInSeconds + completedScreenShareDurationInMillis) / MILLIS_IN_SECOND
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
    }
}
