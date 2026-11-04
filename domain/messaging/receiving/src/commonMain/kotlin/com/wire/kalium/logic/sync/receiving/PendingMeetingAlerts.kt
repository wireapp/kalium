/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.meeting.MeetingReminder
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock
import kotlin.time.Instant

/** A cancellation outlives its canonical meeting row, so retain the event's presentation snapshot. */
@Serializable
internal data class PendingMeetingAlert(
    @SerialName("eventId") val eventId: String,
    @SerialName("kind") val kind: Kind,
    @SerialName("meeting") val meeting: QualifiedIDEntity,
    @SerialName("conversation") val conversation: QualifiedIDEntity,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String?,
    @SerialName("time") val time: Instant,
    @SerialName("start") val start: Instant,
    @SerialName("end") val end: Instant,
    @SerialName("authorImage") val authorImage: QualifiedIDEntity? = null,
) {
    enum class Kind { INVITE, UPDATE, CANCEL }

    fun notification(): LocalNotification.Meeting {
        val author = author?.let { LocalNotificationMessageAuthor(it, authorImage?.toModel()) }
        return when (kind) {
            Kind.INVITE -> LocalNotification.Meeting.Invite(
                eventId,
                meeting.toModel(),
                conversation.toModel(),
                title,
                author,
                time,
                start,
                end,
            )
            Kind.UPDATE -> LocalNotification.Meeting.Update(
                eventId,
                meeting.toModel(),
                conversation.toModel(),
                title,
                author,
                time,
                start,
                end,
            )
            Kind.CANCEL -> LocalNotification.Meeting.Cancel(
                eventId,
                meeting.toModel(),
                conversation.toModel(),
                title,
                author,
                time,
            )
        }
    }
}

/** Uses the account's existing exclusive owner; delivery is acknowledged only after native acceptance. */
class PendingMeetingAlerts(
    private val database: UserDatabaseBuilder,
    private val now: () -> Instant = { Clock.System.now() },
) {
    fun observeChanges() = database.metadataDAO.valueByKeyFlow(KEY).map { Unit }

    suspend fun read(): List<LocalNotification.Meeting> = database.writeMessageEffects {
        // An old invitation may describe a future meeting. Retain it until acceptance, as the shared handlers do.
        read(metadata).sortedBy { it.time }.map { it.notification() }
    }

    /** Call only after native enqueue succeeded, or the same event identity is already delivered. */
    suspend fun acknowledge(eventId: String, meetingId: MeetingId) {
        database.writeMessageEffects {
            write(metadata, read(metadata).filterNot { it.eventId == eventId && it.meeting == meetingId.toDao() })
        }
    }

    /** The host merges account snapshots and reserves capacity for unrelated pending notifications. */
    suspend fun reminders(): List<MeetingReminder> = database.writeMessageEffects {
        if (!config.isMeetingsEnabled()) return@writeMessageEffects emptyList()
        meetings.getMeetingRemindersWithin(
            now() + MeetingReminder.REMINDER_NOTIFICATION_LEAD_TIME,
            Instant.DISTANT_FUTURE,
        ).map { MeetingReminder(it.occurrenceId, it.meetingId.toModel(), it.conversationId.toModel(), it.title, it.startTime) }
            .sortedWith(compareBy(MeetingReminder::startTime, MeetingReminder::occurrenceId))
    }

    internal companion object {
        const val KEY = "apple.pending-meeting-alerts.v1"
        private val serializer = ListSerializer(PendingMeetingAlert.serializer())

        suspend fun append(metadata: MetadataDAO, alert: PendingMeetingAlert) {
            val records = read(metadata)
            if (records.none { it.eventId == alert.eventId && it.meeting == alert.meeting }) write(metadata, records + alert)
        }

        private suspend fun read(metadata: MetadataDAO): List<PendingMeetingAlert> =
            metadata.getSerializable(KEY, serializer).orEmpty()

        private suspend fun write(metadata: MetadataDAO, records: List<PendingMeetingAlert>) {
            if (records.isEmpty()) metadata.deleteValue(KEY)
            else metadata.putSerializable(KEY, records, serializer)
        }
    }
}

internal suspend fun MessageEffectDAOs.recordMeetingAlert(event: Event.Meeting?, meeting: MeetingEntity?) {
    if (meeting == null || event == null || event is Event.Meeting.Create) return
    val (kind, time, sender) = when (event) {
        is Event.Meeting.MemberAdd -> Triple(PendingMeetingAlert.Kind.INVITE, event.dateTime, event.senderUserId)
        is Event.Meeting.Update -> Triple(PendingMeetingAlert.Kind.UPDATE, event.dateTime, event.senderUserId)
        is Event.Meeting.Delete -> Triple(PendingMeetingAlert.Kind.CANCEL, event.dateTime, event.senderUserId)
        is Event.Meeting.Create -> return
    }
    val author = sender?.let { users.getUserDetailsByQualifiedID(QualifiedIDEntity(it.value, it.domain)) }
    PendingMeetingAlerts.append(
        metadata,
        PendingMeetingAlert(
            eventId = event.id,
            kind = kind,
            meeting = meeting.meetingId,
            conversation = meeting.conversationId,
            title = meeting.title,
            author = author?.let { it.name.orEmpty() },
            time = time,
            start = meeting.startTime,
            end = meeting.endTime,
            authorImage = author?.previewAssetId,
        ),
    )
}

internal suspend fun MessageEffectDAOs.recordMeetingCancellations(
    eventId: String,
    conversation: QualifiedIDEntity,
    time: Instant,
    author: UserId?,
) {
    meetings.getMeetingsByConversationId(conversation).forEach { meeting ->
        recordMeetingAlert(Event.Meeting.Delete(eventId, meeting.meetingId.toModel(), time, author), meeting)
    }
}
