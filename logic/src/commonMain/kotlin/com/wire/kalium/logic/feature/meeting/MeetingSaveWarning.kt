/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.user.UserId

/** A meeting was saved remotely, but a subsequent setup step did not fully succeed. */
public sealed interface MeetingSaveWarning {
    public data object ConversationSetupFailed : MeetingSaveWarning
    public data object RemoveParticipantsFailed : MeetingSaveWarning
    public data class AddParticipantsFailed(val participants: Map<UserId, CoreFailure>) : MeetingSaveWarning
}

internal fun MLSAdditionResult.toMeetingSaveWarning(): MeetingSaveWarning? {
    val failed: Map<UserId, CoreFailure> = usersWithoutKeyPackages.associateWith {
        CoreFailure.MissingKeyPackages(usersWithoutKeyPackages)
    } + usersWithUnreachableBackend.associateWith {
        NetworkFailure.FederatedBackendFailure.FailedDomains(listOf(it.domain))
    }
    return failed.takeIf { it.isNotEmpty() }?.let { MeetingSaveWarning.AddParticipantsFailed(it) }
}

internal fun CoreFailure.toParticipantWarning(users: List<UserId>): MeetingSaveWarning.AddParticipantsFailed {
    val affectedUsers = when (this) {
        is CoreFailure.MissingKeyPackages -> users.filter { it in failedUserIds }
        is NetworkFailure.FederatedBackendFailure.FailedDomains -> users.filter { domains.isEmpty() || it.domain in domains }
        else -> users
    }
    return MeetingSaveWarning.AddParticipantsFailed(affectedUsers.associateWith { this })
}

internal fun CoreFailure.toSetupWarning(users: List<UserId>): MeetingSaveWarning = when (this) {
    is CoreFailure.MissingKeyPackages, is NetworkFailure.FederatedBackendFailure ->
        toParticipantWarning(users).takeIf { it.participants.isNotEmpty() } ?: MeetingSaveWarning.ConversationSetupFailed
    else -> MeetingSaveWarning.ConversationSetupFailed
}
