/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.meeting.MeetingReminder
import com.wire.kalium.logic.data.meeting.toMeetingEntity
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserProfiles
import com.wire.kalium.logic.sync.receiver.meeting.isMeetingNotFound
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlin.time.Clock
import kotlin.time.Instant

internal suspend fun fetchReceivedMeeting(
    meetingId: MeetingId,
    api: MeetingApi,
    users: MembershipUserLoader,
    event: Event.Meeting? = null,
): List<ReceivedPayload> {
    val result = wrapApiRequest { api.fetchMeeting(meetingId) }
    if (result is Either.Left && (result.value is NetworkFailure.FeatureNotSupported || result.value.isMeetingNotFound())) {
        return emptyList()
    }
    return result.orThrow().toMeetingEntity()?.let { meeting ->
        val creatorId = meeting.creatorId.toModel()
        val profiles = if (users.user(creatorId)?.name.isNullOrBlank()) users.refresh(creatorId) else UserProfiles()
        listOf(ReceivedPayload.MeetingUpsert(meeting, profiles, event))
    }.orEmpty()
}

/** The alert the app's meeting event handlers show for the same event. Creation alerts nobody. */
internal suspend fun MessageEffectDAOs.meetingAlert(event: Event.Meeting?, meeting: MeetingEntity?): LocalNotification.Meeting? {
    if (event == null || meeting == null || event is Event.Meeting.Create) return null
    val (sender, time) = when (event) {
        is Event.Meeting.Create -> event.senderUserId to event.dateTime
        is Event.Meeting.MemberAdd -> event.senderUserId to event.dateTime
        is Event.Meeting.Update -> event.senderUserId to event.dateTime
        is Event.Meeting.Delete -> event.senderUserId to event.dateTime
    }
    val author = sender?.let { users.getUserDetailsByQualifiedID(it.toDao()) }
        ?.let { LocalNotificationMessageAuthor(it.name.orEmpty(), it.previewAssetId?.toModel()) }
    val id = meeting.meetingId.toModel()
    val conversation = meeting.conversationId.toModel()
    val (title, start, end) = Triple(meeting.title, meeting.startTime, meeting.endTime)
    return when (event) {
        is Event.Meeting.Create -> null
        is Event.Meeting.MemberAdd -> LocalNotification.Meeting.Invite(event.id, id, conversation, title, author, time, start, end)
        is Event.Meeting.Update -> LocalNotification.Meeting.Update(event.id, id, conversation, title, author, time, start, end)
        is Event.Meeting.Delete -> LocalNotification.Meeting.Cancel(event.id, id, conversation, title, author, time)
    }
}

/** Read before a conversation deletion cascades to its meetings, as the app's conversation handlers do. */
internal suspend fun MessageEffectDAOs.meetingCancellations(
    eventId: String,
    conversation: QualifiedIDEntity,
    time: Instant,
    author: UserId?,
): List<LocalNotification.Meeting> = meetings.getMeetingsByConversationId(conversation).mapNotNull { meeting ->
    meetingAlert(Event.Meeting.Delete(eventId, meeting.meetingId.toModel(), time, author), meeting)
}

/** Reminders the host schedules natively, so edits and cancellations apply while the app is suspended. */
suspend fun UserDatabaseBuilder.upcomingMeetingReminders(now: Instant = Clock.System.now()): List<MeetingReminder> {
    if (!userConfigDAO.isMeetingsEnabled()) return emptyList()
    return meetingDao.getMeetingRemindersWithin(now + MeetingReminder.REMINDER_NOTIFICATION_LEAD_TIME, Instant.DISTANT_FUTURE)
        .map { MeetingReminder(it.occurrenceId, it.meetingId.toModel(), it.conversationId.toModel(), it.title, it.startTime) }
        .sortedWith(compareBy(MeetingReminder::startTime, MeetingReminder::occurrenceId))
}
