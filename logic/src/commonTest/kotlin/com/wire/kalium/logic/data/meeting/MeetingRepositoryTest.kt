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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO
import com.wire.kalium.network.api.authenticated.meeting.toMeetingDTO
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
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
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.wire.kalium.network.api.model.ConversationId as ApiConversationId
import com.wire.kalium.network.api.model.MeetingId as NetworkMeetingId
import com.wire.kalium.network.api.model.UserId as ApiUserId

class MeetingRepositoryTest {

    @Test
    fun whenFetchAndPersistMeetings_thenMeetingsAreFetchedAndPersistedWithNowDateTime() = runTest {
        val meetingDTO = MeetingDTO(
            meetingId = NetworkMeetingId("meeting1", "domain"),
            conversationId = ApiConversationId("conversation1", "domain"),
            creatorId = ApiUserId("user1", "domain"),
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
        val expectedMeetingEntity = requireNotNull(arrangement.meetingMapper.fromApiToDao(meetingDTO))
        val expectedMeeting = arrangement.meetingMapper.fromDaoToModel(expectedMeetingEntity)

        val result = repository.fetchAndPersistMeetings(generateOccurrencesFrom, generateOccurrencesUntil)

        assertTrue(result.isRight())
        assertContentEquals(listOf(expectedMeeting), result.getOrNull())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.fetchMeetings()
            arrangement.meetingDao.upsertMeetings(
                meetings = listOf(expectedMeetingEntity),
                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
            )
        }
    }

    @Test
    fun whenFetchAndPersistMeeting_thenMeetingIsFetchedAndPersistedWithNowDateTime() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val meetingDTO = MeetingDTO(
            meetingId = NetworkMeetingId("meeting1", "domain"),
            conversationId = ApiConversationId("conversation1", "domain"),
            creatorId = ApiUserId("user1", "domain"),
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            updatedAt = null,
            title = "Meeting 1",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            trial = false,
            recurrence = null,
        )
        val (arrangement, repository) = Arrangement()
            .withFetchMeetingSuccess(meetingId, meetingDTO)
            .arrange()
        val generateOccurrencesFrom = Instant.parse("2026-05-01T00:00:00Z")
        val generateOccurrencesUntil = Instant.parse("2026-07-01T00:00:00Z")
        val expectedMeetingEntity = requireNotNull(arrangement.meetingMapper.fromApiToDao(meetingDTO))
        val expectedMeeting = arrangement.meetingMapper.fromDaoToModel(expectedMeetingEntity)

        val result = repository.fetchAndPersistMeeting(meetingId, generateOccurrencesFrom, generateOccurrencesUntil)

        assertTrue(result.isRight())
        assertEquals(expectedMeeting, result.getOrNull())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.fetchMeeting(meetingId = meetingId.toApi())
            arrangement.meetingDao.upsertMeetings(
                meetings = listOf(expectedMeetingEntity),
                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
            )
        }
    }

    @Test
    fun givenApiFetchMeetingFails_whenFetchAndPersistMeeting_thenReturnsNetworkFailureAndMeetingIsNotPersistedLocally() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val (arrangement, repository) = Arrangement()
            .withFetchMeetingFailure(meetingId)
            .arrange()

        val result = repository.fetchAndPersistMeeting(meetingId)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.fetchMeeting(meetingId.toApi())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.meetingDao.upsertMeetings(any(), any())
        }
    }

    @Test
    fun givenPersistingMeetingFails_whenFetchAndPersistMeeting_thenReturnsStorageFailure() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val meetingDTO = MeetingDTO(
            meetingId = NetworkMeetingId("meeting1", "domain"),
            conversationId = ApiConversationId("conversation1", "domain"),
            creatorId = ApiUserId("user1", "domain"),
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            updatedAt = null,
            title = "Meeting 1",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            trial = false,
            recurrence = null,
        )
        val persistenceException = RuntimeException("An error occurred persisting the meeting")
        val (arrangement, repository) = Arrangement()
            .withFetchMeetingSuccess(meetingId, meetingDTO)
            .withPersistMeetingFailure(persistenceException)
            .arrange()

        val result = repository.fetchAndPersistMeeting(meetingId)

        assertIs<Either.Left<StorageFailure.Generic>>(result).also {
            assertSame(persistenceException, it.value.rootCause)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.fetchMeeting(meetingId.toApi())
            arrangement.meetingDao.upsertMeetings(any(), any())
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

    @Test
    fun givenSuccess_whenCreateNewMeeting_thenMlsGroupIsEstablishedWithParticipantsAndSelf() = runTest {
        val createMeeting = UPSERT_MEETING
        val groupId = "group-id"
        val generateOccurrencesFrom = Instant.parse("2026-05-01T00:00:00Z")
        val generateOccurrencesUntil = Instant.parse("2026-07-01T00:00:00Z")
        val response = upsertMeetingResponse(
            groupId = groupId,
            epoch = 0UL,
        )
        val (arrangement, repository) = Arrangement()
            .withCreateNewMeetingSuccess(createMeeting, response)
            .withPersistConversationsSuccess()
            .withTransactionMlsContext()
            .withMlsGroupEstablished(expectedMLSAdditionResult)
            .arrange()
        val expectedMeetingEntity = requireNotNull(arrangement.meetingMapper.fromApiToDao(response.toMeetingDTO()))

        val result = repository.createNewMeeting(
            meeting = createMeeting,
            generateOccurrencesFrom = generateOccurrencesFrom,
            generateOccurrencesUntil = generateOccurrencesUntil,
            transactionContext = arrangement.transactionContext,
        )

        assertTrue(result.isRight())
        assertEquals(expectedMLSAdditionResult, result.getOrNull())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.createNewMeeting(arrangement.meetingMapper.fromModelToApi(createMeeting))
            arrangement.persistConversations(
                transactionContext = arrangement.transactionContext,
                conversations = listOf(response.conversation),
                invalidateMembers = true,
                reason = ConversationSyncReason.Other
            )
            arrangement.meetingDao.upsertMeetings(
                meetings = listOf(expectedMeetingEntity),
                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(
                mlsContext = arrangement.mlsContext,
                groupID = GroupID(groupId),
                members = createMeeting.otherParticipants + arrangement.selfUserId,
                publicKeys = null,
                allowSkippingUsersWithoutKeyPackages = true
            )
        }
    }

    @Test
    fun givenApiCreateFails_whenCreateNewMeeting_thenLocalStateIsNotUpdated() = runTest {
        val createMeeting = UPSERT_MEETING
        val (arrangement, repository) = Arrangement()
            .withCreateNewMeetingFailure(createMeeting)
            .arrange()

        val result = repository.createNewMeeting(
            meeting = createMeeting,
            transactionContext = arrangement.transactionContext,
        )

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.createNewMeeting(arrangement.meetingMapper.fromModelToApi(createMeeting))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.persistConversations(any(), any(), any(), any())
            arrangement.meetingDao.upsertMeetings(any(), any())
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenPersistConversationsFails_whenCreateNewMeeting_thenMeetingIsNotPersistedAndMlsGroupIsNotEstablished() = runTest {
        val createMeeting = UPSERT_MEETING
        val response = upsertMeetingResponse()
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, repository) = Arrangement()
            .withCreateNewMeetingSuccess(createMeeting, response)
            .withPersistConversationsFailure(failure)
            .arrange()

        val result = repository.createNewMeeting(
            meeting = createMeeting,
            transactionContext = arrangement.transactionContext,
        )

        assertIs<Either.Left<NetworkFailure.NoNetworkConnection>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistConversations(
                transactionContext = arrangement.transactionContext,
                conversations = listOf(response.conversation),
                invalidateMembers = true,
                reason = ConversationSyncReason.Other
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.meetingDao.upsertMeetings(any(), any())
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenMeetingPersistenceFails_whenCreateNewMeeting_thenFailureIsReturnedAndMlsGroupIsNotEstablished() = runTest {
        val createMeeting = UPSERT_MEETING
        val response = upsertMeetingResponse()
        val error = RuntimeException("Meeting persistence failed")
        val (arrangement, repository) = Arrangement()
            .withCreateNewMeetingSuccess(createMeeting, response)
            .withPersistConversationsSuccess()
            .withPersistMeetingFailure(error)
            .arrange()

        val result = repository.createNewMeeting(
            meeting = createMeeting,
            transactionContext = arrangement.transactionContext,
        )

        assertIs<Either.Left<StorageFailure.Generic>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingDao.upsertMeetings(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenEstablishMlsGroupFails_whenCreateNewMeeting_thenEstablishMlsFailureIsReturnedAndRecoveryIsEnqueued() = runTest {
        val createMeeting = UPSERT_MEETING
        val response = upsertMeetingResponse()
        val failure = CoreFailure.MissingKeyPackages(setOf(TestUser.OTHER.id))
        val (arrangement, repository) = Arrangement()
            .withCreateNewMeetingSuccess(createMeeting, response)
            .withPersistConversationsSuccess()
            .withTransactionMlsContext()
            .withMlsGroupEstablishmentFailure(failure)
            .arrange()

        val result = repository.createNewMeeting(
            meeting = createMeeting,
            transactionContext = arrangement.transactionContext,
        )

        val resultFailure = assertIs<Either.Left<MeetingDataSource.EstablishMLSFailure>>(result).value
        assertEquals(response.conversation.id.toModel(), resultFailure.conversationId)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            arrangement.pendingActionsRepository.enqueuePendingMLSGroupJoin(response.conversation.id.toModel())
        }
    }

    @Test
    fun givenMlsGroupAlreadyEstablished_whenCreateNewMeeting_thenMlsGroupIsNotEstablishedAgain() = runTest {
        val createMeeting = UPSERT_MEETING
        val response = upsertMeetingResponse(epoch = 1UL)
        val (arrangement, repository) = Arrangement()
            .withCreateNewMeetingSuccess(createMeeting, response)
            .withPersistConversationsSuccess()
            .arrange()

        val result = repository.createNewMeeting(
            meeting = createMeeting,
            transactionContext = arrangement.transactionContext,
        )

        assertTrue(result.isRight())
        assertEquals(MLSAdditionResult.Empty, result.getOrNull())
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            arrangement.pendingActionsRepository.enqueuePendingMLSGroupJoin(any())
        }
    }

    @Test
    fun givenApiUpdateSucceeds_whenUpdateMeeting_thenMeetingAndConversationArePersisted() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val meeting = UPSERT_MEETING
        val response = upsertMeetingResponse(epoch = 1UL)
        val generateOccurrencesFrom = Instant.parse("2026-05-01T00:00:00Z")
        val generateOccurrencesUntil = Instant.parse("2026-07-01T00:00:00Z")
        val (arrangement, repository) = Arrangement()
            .withUpdateMeetingSuccess(meetingId, meeting, response)
            .withPersistConversationsSuccess()
            .withMlsContext()
            .withConversationMembers(response.conversationId.toModel(), emptyList())
            .arrange()
        val expectedMeetingEntity = requireNotNull(arrangement.meetingMapper.fromApiToDao(response.toMeetingDTO()))

        val result = repository.updateMeeting(
            meetingId = meetingId,
            meeting = meeting,
            generateOccurrencesFrom = generateOccurrencesFrom,
            generateOccurrencesUntil = generateOccurrencesUntil,
            transactionContext = arrangement.transactionContext,
        )

        assertTrue(result.isRight())
        assertEquals(MLSAdditionResult.Empty, result.getOrNull())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.updateMeeting(
                meetingId = meetingId.toApi(),
                request = arrangement.meetingMapper.fromModelToApi(meeting)
            )
            arrangement.persistConversations(
                transactionContext = arrangement.transactionContext,
                conversations = listOf(response.conversation),
                invalidateMembers = true,
                reason = ConversationSyncReason.Other,
            )
            arrangement.meetingDao.upsertMeetings(
                meetings = listOf(expectedMeetingEntity),
                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
            )
        }
    }

    @Test
    fun givenApiUpdateFails_whenUpdateMeeting_thenMeetingIsNotPersistedLocally() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val meeting = UPSERT_MEETING
        val (arrangement, repository) = Arrangement()
            .withUpdateMeetingFailure(meetingId, meeting)
            .arrange()

        val result = repository.updateMeeting(
            meetingId = meetingId,
            meeting = meeting,
            transactionContext = arrangement.transactionContext,
        )

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.updateMeeting(
                meetingId = meetingId.toApi(),
                request = arrangement.meetingMapper.fromModelToApi(meeting)
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.persistConversations(any(), any(), any(), any())
            arrangement.meetingDao.upsertMeetings(any(), any())
        }
    }

    @Test
    fun givenPersistingMeetingFails_whenUpdateMeeting_thenReturnsStorageFailureAndDoesNotUpdateMlsMembers() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val meeting = UPSERT_MEETING
        val response = upsertMeetingResponse(epoch = 1UL)
        val generateOccurrencesFrom = Instant.parse("2026-05-01T00:00:00Z")
        val generateOccurrencesUntil = Instant.parse("2026-07-01T00:00:00Z")
        val persistenceException = RuntimeException("An error occurred persisting the meeting")
        val (arrangement, repository) = Arrangement()
            .withUpdateMeetingSuccess(meetingId, meeting, response)
            .withPersistConversationsSuccess()
            .withPersistMeetingFailure(persistenceException)
            .arrange()
        val expectedMeetingEntity = requireNotNull(arrangement.meetingMapper.fromApiToDao(response.toMeetingDTO()))

        val result = repository.updateMeeting(
            meetingId = meetingId,
            meeting = meeting,
            generateOccurrencesFrom = generateOccurrencesFrom,
            generateOccurrencesUntil = generateOccurrencesUntil,
            transactionContext = arrangement.transactionContext,
        )

        assertIs<Either.Left<StorageFailure.Generic>>(result).also {
            assertSame(persistenceException, it.value.rootCause)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingApi.updateMeeting(
                meetingId = meetingId.toApi(),
                request = arrangement.meetingMapper.fromModelToApi(meeting)
            )
            arrangement.persistConversations(
                transactionContext = arrangement.transactionContext,
                conversations = listOf(response.conversation),
                invalidateMembers = true,
                reason = ConversationSyncReason.Other,
            )
            arrangement.meetingDao.upsertMeetings(
                meetings = listOf(expectedMeetingEntity),
                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.getConversationMembers(any())
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            arrangement.mlsConversationRepository.addMemberToMLSGroup(any(), any(), any(), any(), any())
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(any(), any(), any())
        }
    }

    @Test
    fun givenMeetingConversationMlsGroupIsNotEstablished_whenUpdateMeeting_thenEstablishesMlsGroup() = runTest {
        val meetingId = MeetingId("meeting1", "domain")
        val groupId = GroupID("group-id")
        val meeting = UPSERT_MEETING
        val response = upsertMeetingResponse(
            protocol = ConvProtocol.MLS,
            groupId = groupId.value,
            epoch = 0UL,
            mlsCipherSuiteTag = 1,
        )
        val expectedMlsAdditionResult = MLSAdditionResult(setOf(TestUser.OTHER.id), emptySet(), emptySet())
        val (arrangement, repository) = Arrangement()
            .withUpdateMeetingSuccess(meetingId, meeting, response)
            .withPersistConversationsSuccess()
            .withMlsContext()
            .withEstablishMlsGroupSuccess(groupId, meeting.otherParticipants + TestUser.SELF.id, expectedMlsAdditionResult)
            .withConversationMembers(response.conversationId.toModel(), meeting.otherParticipants)
            .arrange()

        val result = repository.updateMeeting(
            meetingId = meetingId,
            meeting = meeting,
            transactionContext = arrangement.transactionContext,
        )

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistConversations(
                transactionContext = arrangement.transactionContext,
                conversations = listOf(response.conversation),
                invalidateMembers = true,
                reason = ConversationSyncReason.Other,
            )
            arrangement.mlsConversationRepository.establishMLSGroup(
                mlsContext = arrangement.mlsContext,
                groupID = groupId,
                members = meeting.otherParticipants + TestUser.SELF.id,
                publicKeys = null,
                allowSkippingUsersWithoutKeyPackages = true
            )
            arrangement.conversationRepository.getConversationMembers(conversationId = response.conversationId.toModel())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(any(), any(), any(), any(), any())
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(any(), any(), any())
        }
    }

    @Test
    fun givenEstablishedMlsGroupAndParticipantsChanged_whenUpdateMeeting_thenMlsMembersAreUpdated() = runTest {
        val participantToKeep = UserId("participant-keep", "domain")
        val participantToAdd = UserId("participant-add", "domain")
        val participantToRemove = UserId("participant-remove", "domain")
        val meetingId = MeetingId("meeting1", "domain")
        val meeting = UPSERT_MEETING.copy(otherParticipants = listOf(participantToKeep, participantToAdd))
        val groupId = GroupID("group-id")
        val mlsCipherSuiteTag = 1
        val response = upsertMeetingResponse(
            protocol = ConvProtocol.MLS,
            groupId = groupId.value,
            epoch = 1UL,
            mlsCipherSuiteTag = mlsCipherSuiteTag
        )
        val expectedMlsAdditionResult = MLSAdditionResult(setOf(participantToAdd), emptySet(), emptySet())
        val (arrangement, repository) = Arrangement()
            .withUpdateMeetingSuccess(meetingId, meeting, response)
            .withPersistConversationsSuccess()
            .withMlsContext()
            .withConversationMembers(response.conversationId.toModel(), listOf(participantToKeep, participantToRemove))
            .withRemoveMembersFromMlsGroupSuccess(groupId, listOf(participantToRemove))
            .withAddMembersToMlsGroupSuccess(groupId, listOf(participantToAdd), expectedMlsAdditionResult)
            .arrange()

        val result = repository.updateMeeting(
            meetingId = meetingId,
            meeting = meeting,
            transactionContext = arrangement.transactionContext,
        )

        assertTrue(result.isRight())
        assertEquals(expectedMlsAdditionResult, result.getOrNull())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.getConversationMembers(conversationId = response.conversationId.toModel())
            arrangement.mlsConversationRepository.removeMembersFromMLSGroup(
                mlsContext = arrangement.mlsContext,
                groupID = groupId,
                userIdList = listOf(participantToRemove),
            )
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                mlsContext = arrangement.mlsContext,
                groupID = groupId,
                userIdList = listOf(participantToAdd),
                cipherSuite = CipherSuite.fromTag(mlsCipherSuiteTag),
                allowPartialMemberList = true,
            )
        }
    }

    inner class Arrangement {
        internal val selfUserId = TestUser.SELF.id
        internal val meetingDao = mock<MeetingDao>(mode = MockMode.autoUnit)
        internal val meetingApi = mock<MeetingApi>(mode = MockMode.autoUnit)
        internal val persistConversations = mock<PersistConversationsUseCase>(mode = MockMode.autoUnit)
        internal val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        internal val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        internal val pendingActionsRepository = mock<PendingActionsRepository>(mode = MockMode.autoUnit)
        internal val transactionContext = mock<CryptoTransactionContext>(mode = MockMode.autoUnit)
        internal val mlsContext = mock<MlsCoreCryptoContext>(mode = MockMode.autoUnit)
        internal val meetingMapper = MapperProvider.meetingMapper()
        internal val conversationMapper = MapperProvider.conversationMapper(selfUserId)
        internal val idMapper = MapperProvider.idMapper()

        internal fun withFetchMeetingsSuccess(result: List<MeetingDTO>) = apply {
            everySuspend { meetingApi.fetchMeetings() } returns NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value)
        }

        internal fun withFetchMeetingSuccess(id: MeetingId, result: MeetingDTO) = apply {
            everySuspend { meetingApi.fetchMeeting(id.toApi()) } returns NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value)
        }

        internal fun withFetchMeetingFailure(id: MeetingId) = apply {
            everySuspend { meetingApi.fetchMeeting(id.toApi()) } returns NetworkResponse.Error(TestNetworkException.generic)
        }

        internal fun withDeleteMeetingSuccess(meetingId: MeetingId) = apply {
            everySuspend { meetingApi.deleteMeeting(meetingId.toApi()) } returns NetworkResponse.Success(
                Unit,
                mapOf(),
                HttpStatusCode.OK.value
            )
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

        internal fun withCreateNewMeetingSuccess(meeting: UpsertMeeting, response: UpsertMeetingResponse) = apply {
            everySuspend {
                meetingApi.createNewMeeting(meetingMapper.fromModelToApi(meeting)) } returns NetworkResponse.Success(
                value = response,
                headers = mapOf(),
                httpCode = HttpStatusCode.Created.value
            )
        }

        internal fun withCreateNewMeetingFailure(meeting: UpsertMeeting) = apply {
            everySuspend {
                meetingApi.createNewMeeting(meetingMapper.fromModelToApi(meeting))
            } returns NetworkResponse.Error(TestNetworkException.generic)
        }

        internal fun withPersistConversationsSuccess() = apply {
            everySuspend { persistConversations(any(), any(), any(), any()) } returns Either.Right(Unit)
        }

        internal fun withPersistConversationsFailure(failure: CoreFailure) = apply {
            everySuspend { persistConversations(any(), any(), any(), any()) } returns Either.Left(failure)
        }

        internal fun withPersistMeetingFailure(error: RuntimeException) = apply {
            everySuspend { meetingDao.upsertMeetings(any(), any()) } throws error
        }

        internal fun withTransactionMlsContext() = apply {
            every { transactionContext.mls } returns mlsContext
        }

        internal fun withMlsGroupEstablished(result: MLSAdditionResult) = apply {
            everySuspend { mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any()) } returns Either.Right(result)
        }

        internal fun withMlsGroupEstablishmentFailure(failure: CoreFailure) = apply {
            everySuspend { mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any()) } returns Either.Left(failure)
        }

        internal fun withUpdateMeetingSuccess(meetingId: MeetingId, meeting: UpsertMeeting, result: UpsertMeetingResponse) = apply {
            everySuspend {
                meetingApi.updateMeeting(meetingId = meetingId.toApi(), request = meetingMapper.fromModelToApi(meeting))
            } returns NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value)
        }

        internal fun withUpdateMeetingFailure(meetingId: MeetingId, meeting: UpsertMeeting) = apply {
            everySuspend {
                meetingApi.updateMeeting(meetingId = meetingId.toApi(), request = meetingMapper.fromModelToApi(meeting))
            } returns NetworkResponse.Error(TestNetworkException.generic)
        }

        internal fun withMlsContext() = apply {
            every { transactionContext.mls } returns mlsContext
        }

        internal fun withConversationMembers(conversationId: ConversationId, result: List<UserId>) = apply {
            everySuspend { conversationRepository.getConversationMembers(conversationId) } returns Either.Right(result)
        }

        internal fun withRemoveMembersFromMlsGroupSuccess(groupId: GroupID, members: List<UserId>) = apply {
            everySuspend {
                mlsConversationRepository.removeMembersFromMLSGroup(any(), groupId, members)
            } returns Either.Right(Unit)
        }

        internal fun withAddMembersToMlsGroupSuccess(groupId: GroupID, members: List<UserId>, result: MLSAdditionResult) = apply {
            everySuspend {
                mlsConversationRepository.addMemberToMLSGroup(any(), groupId, members, any(), true)
            } returns Either.Right(result)
        }

        internal fun withEstablishMlsGroupSuccess(groupId: GroupID, members: List<UserId>, result: MLSAdditionResult) = apply {
            everySuspend {
                mlsConversationRepository.establishMLSGroup(any(), groupId, members, null, true)
            } returns Either.Right(result)
        }

        internal fun arrange() = this to MeetingDataSource(
            selfUserId = selfUserId,
            meetingDAO = meetingDao,
            meetingApi = meetingApi,
            persistConversations = persistConversations,
            mlsConversationRepository = mlsConversationRepository,
            conversationRepository = conversationRepository,
            pendingActionsRepository = pendingActionsRepository,
            meetingMapper = meetingMapper,
            conversationMapper = conversationMapper,
            idMapper = idMapper,
        )
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

    private val UPSERT_MEETING = UpsertMeeting(
        title = "Meeting 1",
        startTime = Instant.parse("2026-06-01T10:00:00Z"),
        endTime = Instant.parse("2026-06-01T11:00:00Z"),
        recurrence = Meeting.Recurrence(
            frequency = Meeting.Recurrence.Frequency.WEEKLY,
            interval = 1L,
            until = Instant.parse("2026-12-01T00:00:00Z")
        ),
        otherParticipants = listOf(TestUser.OTHER.id)
    )

    private val expectedMLSAdditionResult = MLSAdditionResult(setOf(TestUser.OTHER.id), emptySet(), emptySet())

    private fun upsertMeetingResponse(
        protocol: ConvProtocol = ConvProtocol.MLS,
        groupId: String? = "group-id",
        epoch: ULong? = 0UL,
        mlsCipherSuiteTag: Int? = null,
        recurrence: MeetingRecurrenceDTO? = MeetingRecurrenceDTO(
            frequency = MeetingFrequencyDTO.WEEKLY,
            interval = 1L,
            until = Instant.parse("2026-12-01T00:00:00Z")
        ),
        conversationId: ApiConversationId = ApiConversationId("conversation1", "domain")
    ) = UpsertMeetingResponse(
        meetingId = NetworkMeetingId("meeting1", "domain"),
        conversationId = conversationId,
        creatorId = ApiUserId("user1", "domain"),
        createdAt = Instant.parse("2026-06-01T00:00:00Z"),
        updatedAt = null,
        title = "Meeting 1",
        startTime = Instant.parse("2026-06-01T10:00:00Z"),
        endTime = Instant.parse("2026-06-01T11:00:00Z"),
        trial = false,
        recurrence = recurrence,
        conversation = ConversationRepositoryTest.CONVERSATION_RESPONSE.copy(
            id = conversationId,
            groupId = groupId,
            epoch = epoch,
            protocol = protocol,
            mlsCipherSuiteTag = mlsCipherSuiteTag,
            conversationGroupType = ConversationResponse.GroupType.Meeting,
        )
    )
}
