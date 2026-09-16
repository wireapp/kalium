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

package com.wire.kalium.logic.data.meeting

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.meeting.Meeting.Recurrence.Companion.SUPPORTED_RECURRENCES
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingRequest
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity.RecurrenceEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrenceDetailsEntity

internal interface MeetingMapper {
    fun fromApiToDao(meeting: MeetingDTO): MeetingEntity?
    fun fromApiToDao(recurrence: MeetingRecurrenceDTO): RecurrenceEntity?
    fun fromDaoToApi(recurrence: RecurrenceEntity): MeetingRecurrenceDTO
    fun fromDaoToModel(meeting: MeetingEntity): Meeting
    fun fromDaoToModel(meeting: MeetingOccurrenceDetailsEntity): MeetingOccurrence
    fun fromDaoToModel(recurrence: RecurrenceEntity): Meeting.Recurrence
    fun fromModelToDao(recurrence: Meeting.Recurrence): RecurrenceEntity
    fun fromModelToApi(upsertMeeting: UpsertMeeting): UpsertMeetingRequest
}

internal class MeetingMapperImpl(private val idMapper: IdMapper = MapperProvider.idMapper()) : MeetingMapper {
    override fun fromApiToDao(meeting: MeetingDTO): MeetingEntity? {
        val recurrence = meeting.recurrence?.let { fromApiToDao(it) }
        return if (meeting.recurrence != null && recurrence == null) {
            null // it means the recurrence is not supported, so the meeting is ignored
        } else {
            MeetingEntity(
                meetingId = idMapper.fromApiToDao(meeting.meetingId),
                conversationId = idMapper.fromApiToDao(meeting.conversationId),
                creatorId = idMapper.fromApiToDao(meeting.creatorId),
                createdAt = meeting.createdAt,
                updatedAt = meeting.updatedAt,
                title = meeting.title,
                startTime = meeting.startTime,
                endTime = meeting.endTime,
                tzid = meeting.tzid,
                recurrence = recurrence
            )
        }
    }

    override fun fromApiToDao(recurrence: MeetingRecurrenceDTO): RecurrenceEntity? = recurrence.frequency.toDaoFrequency()
        ?.let { RecurrenceEntity(frequency = it, interval = recurrence.interval, until = recurrence.until) }
        ?.takeIf { it.isSupported() }

    override fun fromDaoToApi(recurrence: RecurrenceEntity): MeetingRecurrenceDTO =
        MeetingRecurrenceDTO(frequency = recurrence.frequency.toApiFrequency(), interval = recurrence.interval, until = recurrence.until)

    override fun fromDaoToModel(meeting: MeetingEntity): Meeting = Meeting(
        meetingId = meeting.meetingId.toModel(),
        conversationId = meeting.conversationId.toModel(),
        creatorId = meeting.creatorId.toModel(),
        title = meeting.title,
        startTime = meeting.startTime,
        endTime = meeting.endTime,
        tzid = meeting.tzid,
        recurrence = meeting.recurrence?.let { fromDaoToModel(it) },
    )

    override fun fromDaoToModel(meeting: MeetingOccurrenceDetailsEntity): MeetingOccurrence = MeetingOccurrence(
        meeting = fromDaoToModel(meeting.meeting),
        conversationName = meeting.conversationName.orEmpty(),
        conversationType = when (meeting.conversationType) {
            ConversationEntity.Type.ONE_ON_ONE -> MeetingOccurrence.ConversationType.OneOnOne(
                previewPicture = meeting.otherUserPreviewAssetId?.toModel()
            )

            ConversationEntity.Type.MEETING -> MeetingOccurrence.ConversationType.Meeting(
                previewPictures = meeting.participantPreviewAssetIds.map { it.toModel() }
            )

            ConversationEntity.Type.CHANNEL -> MeetingOccurrence.ConversationType.Channel(
                isPrivateChannel = meeting.channelAccess != ConversationEntity.ChannelAccess.PUBLIC
            )

            else -> MeetingOccurrence.ConversationType.Group
        },
        selfRole = when (meeting.meeting.creatorId) {
            meeting.selfUserId -> MeetingOccurrence.SelfRole.Creator
            else -> MeetingOccurrence.SelfRole.Member
        },
        occurrenceId = meeting.occurrence.occurrenceId,
        occurrenceStartTime = meeting.occurrence.occurrenceStart,
        occurrenceEndTime = meeting.occurrence.occurrenceEnd
    )

    override fun fromDaoToModel(recurrence: RecurrenceEntity): Meeting.Recurrence = Meeting.Recurrence(
        frequency = recurrence.frequency.toFrequency(),
        interval = recurrence.interval ?: 1L,
        until = recurrence.until
    )

    override fun fromModelToDao(recurrence: Meeting.Recurrence): RecurrenceEntity = RecurrenceEntity(
        frequency = recurrence.frequency.toDaoFrequency(),
        interval = recurrence.interval,
        until = recurrence.until
    )

    override fun fromModelToApi(upsertMeeting: UpsertMeeting): UpsertMeetingRequest = UpsertMeetingRequest(
        title = upsertMeeting.title,
        startTime = upsertMeeting.startTime,
        endTime = upsertMeeting.endTime,
        tzid = upsertMeeting.tzid,
        recurrence = upsertMeeting.recurrence?.let {
            MeetingRecurrenceDTO(
                frequency = it.frequency.toDaoFrequency().toApiFrequency(),
                interval = it.interval,
                until = it.until
            )
        }
    )

    private fun RecurrenceEntity.Frequency.toFrequency(): Meeting.Recurrence.Frequency = when (this) {
        RecurrenceEntity.Frequency.DAILY -> Meeting.Recurrence.Frequency.DAILY
        RecurrenceEntity.Frequency.WEEKLY -> Meeting.Recurrence.Frequency.WEEKLY
    }

    private fun Meeting.Recurrence.Frequency.toDaoFrequency(): RecurrenceEntity.Frequency = when (this) {
        Meeting.Recurrence.Frequency.DAILY -> RecurrenceEntity.Frequency.DAILY
        Meeting.Recurrence.Frequency.WEEKLY -> RecurrenceEntity.Frequency.WEEKLY
    }

    private fun MeetingFrequencyDTO.toDaoFrequency(): RecurrenceEntity.Frequency? = when (this) {
        MeetingFrequencyDTO.DAILY -> RecurrenceEntity.Frequency.DAILY
        MeetingFrequencyDTO.WEEKLY -> RecurrenceEntity.Frequency.WEEKLY
        MeetingFrequencyDTO.MONTHLY,
        MeetingFrequencyDTO.YEARLY -> null
    }

    private fun RecurrenceEntity.Frequency.toApiFrequency(): MeetingFrequencyDTO = when (this) {
        RecurrenceEntity.Frequency.DAILY -> MeetingFrequencyDTO.DAILY
        RecurrenceEntity.Frequency.WEEKLY -> MeetingFrequencyDTO.WEEKLY
    }

    private fun RecurrenceEntity.isSupported() = fromDaoToModel(this).let {
        it.frequency to it.interval in SUPPORTED_RECURRENCES
    }
}
