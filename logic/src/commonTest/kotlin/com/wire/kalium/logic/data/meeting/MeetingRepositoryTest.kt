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

import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.meeting.MeetingDao
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class MeetingRepositoryTest {

    @Test
    fun whenFetchAndPersistMeetings_thenMeetingsAreFetchedAndPersistedWithNowDateTime() = runTest {
        val meetingDTO = MeetingDTO(
            meetingId = MeetingId("meeting1", "domain"),
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

        val result = repository.fetchAndPersistMeetings(generateOccurrencesFrom, generateOccurrencesUntil)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.fetchMeetings()
            arrangement.meetingDao.upsertMeetings(
                meetings = listOf(arrangement.meetingMapper.fromApiToDao(meetingDTO)),
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
            arrangement.meetingDao.insertMissingOccurrences(from = removeOlderThan, until = generateOccurrencesUntil)
        }
    }

    inner class Arrangement {
        internal val meetingDao = mock<MeetingDao>(mode = MockMode.autoUnit)
        internal val meetingApi = mock<MeetingApi>(mode = MockMode.autoUnit)
        internal val meetingMapper = MapperProvider.meetingMapper()

        internal fun withFetchMeetingsSuccess(result: List<MeetingDTO>) = apply {
            everySuspend { meetingApi.fetchMeetings() } returns NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value)
        }
        internal fun arrange() = this to MeetingDataSource(meetingDAO = meetingDao, meetingApi = meetingApi)
    }
}
