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
package com.wire.kalium.logic.data.meeting

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.model.MeetingId as NetworkMeetingId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.meeting.MeetingDao
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrenceDetailsEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrenceEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeetingRepositoryTest {

    @Test
    fun whenFetchAndPersistMeetings_thenMeetingsAreFetchedAndPersistedWithNowDateTime() = runTest {
        val meetingDTO = MeetingDTO(
            meetingId = NetworkMeetingId("meeting1", "domain"),
            conversationId = ConversationId("conversation1", "domain"),
            creatorId = UserId("user1", "domain"),
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            updatedAt = null,
            title = "Meeting 1",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            trial = false,
            recurrence = null,
        )
        val (arrangement, repository) = Arrangement()
            .withFetchMeetingsSuccess(listOf(meetingDTO))
            .arrange()
        val generateOccurrencesFrom = Instant.parse("2026-05-01T00:00:00Z")
        val generateOccurrencesUntil = Instant.parse("2026-07-01T00:00:00Z")
        val expectedMeeting = requireNotNull(arrangement.meetingMapper.fromApiToDao(meetingDTO))

        val result = repository.fetchAndPersistMeetings(generateOccurrencesFrom, generateOccurrencesUntil)

        assertTrue(result.isRight())
        assertContentEquals(listOf(expectedMeeting), result.getOrNull())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.fetchMeetings()
            arrangement.meetingDao.upsertMeetings(
                meetings = listOf(expectedMeeting),
                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
            )
        }
    }

    @Test
    fun whenSyncMeetingOccurrences_thenDaoMethodsAreCalledWithProperDateTimes() = runTest {
        val (arrangement, repository) = Arrangement().arrange()
        val generateOccurrencesUntil = Instant.parse("2026-07-01T00:00:00Z")
        val removeOlderThan = Instant.parse("2026-05-01T00:00:00Z")

        val result = repository.syncMeetingOccurrences(removeOlderThan, generateOccurrencesUntil)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingDao.removeOutdatedMeetings(removeOlderThan)
            arrangement.meetingDao.insertMissingOccurrences(GenerationLimit.Window(removeOlderThan, generateOccurrencesUntil))
        }
    }

    @Test
    fun givenApiDeleteSucceeds_whenDeleteMeeting_thenMeetingIsDeletedLocally() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val (arrangement, repository) = Arrangement()
            .withDeleteMeetingSuccess(meetingId)
            .arrange()

        val result = repository.deleteMeeting(meetingId)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.deleteMeeting(meetingId.toApi())
            arrangement.meetingDao.deleteMeeting(meetingId.toDao())
        }
    }

    @Test
    fun givenApiDeleteFails_whenDeleteMeeting_thenMeetingIsNotDeletedLocally() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val (arrangement, repository) = Arrangement()
            .withDeleteMeetingFailure(meetingId)
            .arrange()

        val result = repository.deleteMeeting(meetingId)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.deleteMeeting(meetingId.toApi())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.meetingDao.deleteMeeting(meetingId.toDao())
        }
    }

    @Suppress("UnusedFlow")
    @Test
    fun givenDaoReturnsOccurrence_whenGetNextMeetingOccurrence_thenReturnsMappedOccurrence() = runTest {
        val meetingId = MEETING_OCCURRENCE_DETAILS.meeting.meetingId.toModel()
        val from = Instant.parse("2026-06-01T10:00:00Z")
        val occurrenceId = MEETING_OCCURRENCE_DETAILS.occurrence.occurrenceId
        val (arrangement, repository) = Arrangement()
            .withNextMeetingOccurrenceId(meetingId, from, occurrenceId)
            .withMeetingOccurrenceDetailsFlow(occurrenceId, flowOf(MEETING_OCCURRENCE_DETAILS))
            .arrange()

        val result = repository.getNextMeetingOccurrence(meetingId, from)

        assertIs<Either.Right<MeetingOccurrence>>(result).also {
            assertEquals(arrangement.meetingMapper.fromDaoToModel(MEETING_OCCURRENCE_DETAILS), result.value)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingDao.getNextMeetingOccurrenceDetailsId(meetingId.toDao(), from)
            arrangement.meetingDao.getMeetingOccurrenceDetailsFlow(occurrenceId)
        }
    }

    @Test
    fun givenDaoReturnsNoOccurrenceId_whenGetNextMeetingOccurrence_thenReturnDataNotFound() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val from = Instant.parse("2026-06-01T10:00:00Z")
        val (arrangement, repository) = Arrangement()
            .withNextMeetingOccurrenceId(meetingId, from, null)
            .arrange()

        val result = repository.getNextMeetingOccurrence(meetingId, from)

        assertIs<Either.Left<StorageFailure.DataNotFound>>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.meetingDao.getNextMeetingOccurrenceDetailsId(meetingId.toDao(), from) }
    }

    inner class Arrangement {
        internal val meetingDao = mock<MeetingDao>(mode = MockMode.autoUnit)
        internal val meetingApi = mock<MeetingApi>(mode = MockMode.autoUnit)
        internal val meetingMapper = MapperProvider.meetingMapper()

        internal fun withFetchMeetingsSuccess(result: List<MeetingDTO>) = apply {
            everySuspend { meetingApi.fetchMeetings() } returns NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value)
        }

        internal fun withDeleteMeetingSuccess(meetingId: MeetingId) = apply {
            everySuspend { meetingApi.deleteMeeting(meetingId.toApi()) } returns NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value)
        }

        internal fun withDeleteMeetingFailure(meetingId: MeetingId) = apply {
            everySuspend { meetingApi.deleteMeeting(meetingId.toApi()) } returns NetworkResponse.Error(TestNetworkException.generic)
        }

        internal fun withNextMeetingOccurrenceId(meetingId: MeetingId, from: Instant, result: String?) = apply {
            everySuspend { meetingDao.getNextMeetingOccurrenceDetailsId(meetingId.toDao(), from) } returns result
        }

        internal fun withMeetingOccurrenceDetailsFlow(occurrenceId: String, result: Flow<MeetingOccurrenceDetailsEntity?>) = apply {
            everySuspend { meetingDao.getMeetingOccurrenceDetailsFlow(occurrenceId) } returns result
        }

        internal fun arrange() = this to MeetingDataSource(meetingDAO = meetingDao, meetingApi = meetingApi)
    }

    private val MEETING_ENTITY = MeetingEntity(
        meetingId = QualifiedIDEntity("meeting1", "domain"),
        conversationId = QualifiedIDEntity("conversation1", "domain"),
        creatorId = QualifiedIDEntity("creator1", "domain"),
        createdAt = Instant.parse("2026-06-01T08:00:00Z"),
        updatedAt = null,
        title = "Meeting 1",
        startTime = Instant.parse("2026-06-01T10:00:00Z"),
        endTime = Instant.parse("2026-06-01T11:00:00Z"),
        trial = false,
        recurrence = null,
    )
    private val MEETING_OCCURRENCE_DETAILS = MeetingOccurrenceDetailsEntity(
        occurrence = MeetingOccurrenceEntity(
            occurrenceId = "occurrence1",
            meetingId = MEETING_ENTITY.meetingId,
            occurrenceStart = Instant.parse("2026-06-01T10:00:00Z"),
            occurrenceEnd = Instant.parse("2026-06-01T11:00:00Z"),
        ),
        meeting = MEETING_ENTITY,
        conversationName = "Conversation 1",
        conversationType = ConversationEntity.Type.GROUP,
        otherUserPreviewAssetId = null,
        channelAccess = null,
        selfUserId = MEETING_ENTITY.creatorId,
    )
}
