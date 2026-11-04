/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PendingMeetingAlertsTest {
    @Test
    fun authorSnapshotKeepsQualifiedAvatarAndKnownAuthorsWithoutAName() = runTest {
        withTestUserDatabase(self) { database ->
            val db = database.builder
            val image = QualifiedIDEntity("avatar", "assets.example.com")
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self))
            db.userDAO.updateUser(PartialUserEntity(self, previewAssetId = image))
            db.writeMessageEffects {
                recordMeetingAlert(Event.Meeting.MemberAdd("unnamed", meeting.meetingId.toModel(), time, self.toModel()), meeting)
            }
            val expectedUnnamed = LocalNotificationMessageAuthor("", image.toModel())
            assertEquals(expectedUnnamed, PendingMeetingAlerts(db) { time }.read().single().author)
            db.userDAO.updateUser(PartialUserEntity(self, name = "Alex"))
            db.writeMessageEffects {
                recordMeetingAlert(Event.Meeting.Update("named", meeting.meetingId.toModel(), time, self.toModel()), meeting)
                recordMeetingAlert(Event.Meeting.Delete("cancel", meeting.meetingId.toModel(), time, self.toModel()), meeting)
            }
            val reopened = PendingMeetingAlerts(db) { time }.read()
            assertEquals(expectedUnnamed, reopened[0].author)
            assertEquals(List(2) { LocalNotificationMessageAuthor("Alex", image.toModel()) }, reopened.drop(1).map { it.author })
        }
    }

    @Test
    fun conversationRemovalKeepsEachCancelledMeetingAndLeavesOtherConversationsAlone() = runTest {
        withTestUserDatabase(self) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self))
            val other = conversation.copy(id = QualifiedIDEntity("other-conversation", self.domain))
            db.conversationDAO.insertConversations(listOf(conversation, other))
            val second = meeting.copy(meetingId = QualifiedIDEntity("second", self.domain))
            val untouched = meeting.copy(meetingId = QualifiedIDEntity("untouched", self.domain), conversationId = other.id)
            db.meetingDao.upsertMeetings(listOf(meeting, second, untouched), window)
            val before = assertNotNull(db.meetingDao.getMeeting(untouched.meetingId))
            db.writeMessageEffects {
                recordMeetingCancellations("conversation-removal", conversation.id, time, null)
                conversations.deleteConversationByQualifiedID(conversation.id)
            }
            val alerts = PendingMeetingAlerts(db) { time }.read()
            assertEquals(setOf(meeting.meetingId.toModel(), second.meetingId.toModel()), alerts.map { it.meetingId }.toSet())
            alerts.forEach { assertIs<LocalNotification.Meeting.Cancel>(it) }
            assertEquals(before, db.meetingDao.getMeeting(untouched.meetingId))
        }
    }

    @Test
    fun meetingEventsSurviveReopeningAndCancellationDeletionUntilNativeAcceptance() = runTest {
        withTestUserDatabase(self) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self))
            db.conversationDAO.insertConversations(listOf(conversation))
            db.writeMessageEffects {
                meetings.upsertMeetings(listOf(meeting), window)
                recordMeetingAlert(Event.Meeting.Create("create", meeting.meetingId.toModel(), time, null), meeting)
                recordMeetingAlert(Event.Meeting.MemberAdd("invite", meeting.meetingId.toModel(), time, null), meeting)
                recordMeetingAlert(Event.Meeting.Update("update", meeting.meetingId.toModel(), time + 1.minutes, null), meeting)
                recordMeetingAlert(Event.Meeting.Delete("cancel", meeting.meetingId.toModel(), time + 2.minutes, null), meeting)
                meetings.deleteMeeting(meeting.meetingId)
            }
            assertNull(db.meetingDao.getMeeting(meeting.meetingId))
            val pending = PendingMeetingAlerts(db) { time + 3.minutes }
            val alerts = pending.read()
            assertEquals(listOf("invite", "update", "cancel"), alerts.map { it.eventId })
            assertIs<LocalNotification.Meeting.Invite>(alerts[0])
            assertIs<LocalNotification.Meeting.Update>(alerts[1])
            assertIs<LocalNotification.Meeting.Cancel>(alerts[2])
            assertEquals(meeting.title, alerts[2].meetingTitle)
            pending.acknowledge("invite", meeting.meetingId.toModel())
            assertEquals(listOf("update", "cancel"), PendingMeetingAlerts(db) { time + 3.minutes }.read().map { it.eventId })
        }
    }

    @Test
    fun replayDeduplicatesAnEventPerMeetingAndAcknowledgementKeepsOtherMeetings() = runTest {
        withTestUserDatabase(self) { database ->
            val db = database.builder
            val other = meeting.copy(meetingId = QualifiedIDEntity("other", self.domain))
            db.writeMessageEffects {
                listOf(meeting, meeting, other).forEach {
                    recordMeetingAlert(Event.Meeting.MemberAdd("event", it.meetingId.toModel(), time, null), it)
                }
            }
            val pending = PendingMeetingAlerts(db) { time }
            assertEquals(2, pending.read().size)
            pending.acknowledge("event", meeting.meetingId.toModel())
            assertEquals(other.meetingId.toModel(), pending.read().single().meetingId)
        }
    }

    @Test
    fun failedTransactionDoesNotLeaveAnAlertAndExpiredEventsAreRetired() = runTest {
        withTestUserDatabase(self) { database ->
            val db = database.builder
            assertFailsWith<IllegalStateException> {
                db.writeMessageEffects {
                    recordMeetingAlert(Event.Meeting.Delete("rolled-back", meeting.meetingId.toModel(), time, null), meeting)
                    error("Rollback canonical meeting effects and their alert together")
                }
            }
            assertEquals(emptyList(), PendingMeetingAlerts(db) { time }.read())
            db.writeMessageEffects {
                recordMeetingAlert(Event.Meeting.Update("old", meeting.meetingId.toModel(), time, null), meeting)
            }
            assertEquals(emptyList(), PendingMeetingAlerts(db) { Instant.DISTANT_FUTURE }.read())
        }
    }

    @Test
    fun reminderSnapshotReflectsMovedAndDeletedOccurrences() = runTest {
        withTestUserDatabase(self) { database ->
            val db = database.builder
            db.userConfigDAO.setMeetingsEnabled(true)
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self))
            db.conversationDAO.insertConversations(listOf(conversation))
            db.meetingDao.upsertMeetings(listOf(meeting), window)
            val pending = PendingMeetingAlerts(db) { time }
            assertEquals(meeting.startTime, pending.reminders().single().startTime)
            val moved = meeting.copy(startTime = time + 3.hours, endTime = time + 4.hours)
            db.meetingDao.upsertMeetings(listOf(moved), window)
            assertEquals(moved.startTime, pending.reminders().single().startTime)
            db.userConfigDAO.setMeetingsEnabled(false)
            assertEquals(emptyList(), pending.reminders())
            db.userConfigDAO.setMeetingsEnabled(true)
            db.meetingDao.deleteMeeting(meeting.meetingId)
            assertEquals(emptyList(), pending.reminders())
        }
    }

    private companion object {
        val self = QualifiedIDEntity("meeting-alert-owner", "example.com")
        val time = Instant.parse("2026-11-04T12:00:00Z")
        val window = GenerationLimit.Window(time, time + 24.hours)
        val meeting = MeetingEntity(
            QualifiedIDEntity("meeting", self.domain), QualifiedIDEntity("conversation", self.domain), self,
            time, null, "Planning", time + 1.hours, time + 2.hours, "UTC", null,
        )
        val conversation = ConversationEntity(
            id = meeting.conversationId, name = "Planning", type = ConversationEntity.Type.GROUP, teamId = null,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus, creatorId = self.value,
            lastNotificationDate = null, lastModifiedDate = time, lastReadDate = time,
            access = listOf(ConversationEntity.Access.INVITE), accessRole = listOf(ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED, messageTimer = null, userMessageTimer = null,
            archived = false, archivedInstant = null,
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            channelAccess = ConversationEntity.ChannelAccess.PRIVATE,
            channelAddPermission = ConversationEntity.ChannelAddPermission.EVERYONE, wireCell = null,
            historySharingRetentionSeconds = 0,
        )
    }
}
