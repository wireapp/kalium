/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.meeting.toMeetingEntity
import com.wire.kalium.logic.data.user.UserProfiles
import com.wire.kalium.logic.sync.receiver.meeting.isMeetingNotFound
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.model.MeetingId

internal suspend fun fetchReceivedMeeting(
    meetingId: MeetingId,
    api: MeetingApi,
    users: MembershipUserLoader,
): List<ReceivedPayload> {
    val result = wrapApiRequest { api.fetchMeeting(meetingId) }
    if (result is Either.Left && (result.value is NetworkFailure.FeatureNotSupported || result.value.isMeetingNotFound())) {
        return emptyList()
    }
    return result.orThrow().toMeetingEntity()?.let { meeting ->
        val creatorId = meeting.creatorId.toModel()
        val profiles = if (users.user(creatorId)?.name.isNullOrBlank()) users.refresh(creatorId) else UserProfiles()
        listOf(ReceivedPayload.MeetingUpsert(meeting, profiles))
    }.orEmpty()
}
