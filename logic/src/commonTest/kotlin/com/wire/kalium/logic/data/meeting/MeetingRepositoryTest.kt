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
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.meeting.CreateMeetingResponse
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO
import com.wire.kalium.network.api.authenticated.meeting.toMeetingDTO
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.meeting.MeetingDao
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.wire.kalium.network.api.model.MeetingId as NetworkMeetingId

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

    @Test
    fun givenSuccess_whenCreateNewMeeting_thenMlsGroupIsEstablishedWithParticipantsAndSelf() = runTest {
        val createMeeting = CREATE_MEETING
        val groupId = "group-id"
        val generateOccurrencesFrom = Instant.parse("2026-05-01T00:00:00Z")
        val generateOccurrencesUntil = Instant.parse("2026-07-01T00:00:00Z")
        val response = createMeetingResponse(
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
        val createMeeting = CREATE_MEETING
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
        val createMeeting = CREATE_MEETING
        val response = createMeetingResponse()
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
        val createMeeting = CREATE_MEETING
        val response = createMeetingResponse()
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
        val createMeeting = CREATE_MEETING
        val response = createMeetingResponse()
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
        val createMeeting = CREATE_MEETING
        val response = createMeetingResponse(epoch = 1UL)
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

    inner class Arrangement {
        internal val selfUserId = TestUser.SELF.id
        internal val meetingDao = mock<MeetingDao>(mode = MockMode.autoUnit)
        internal val meetingApi = mock<MeetingApi>(mode = MockMode.autoUnit)
        internal val persistConversations = mock<PersistConversationsUseCase>(mode = MockMode.autoUnit)
        internal val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        internal val pendingActionsRepository = mock<PendingActionsRepository>(mode = MockMode.autoUnit)
        internal val transactionContext = mock<CryptoTransactionContext>(mode = MockMode.autoUnit)
        internal val mlsContext = mock<MlsCoreCryptoContext>(mode = MockMode.autoUnit)
        internal val meetingMapper = MapperProvider.meetingMapper()
        internal val conversationMapper = MapperProvider.conversationMapper(selfUserId)
        internal val idMapper = MapperProvider.idMapper()

        internal fun withFetchMeetingsSuccess(result: List<MeetingDTO>) = apply {
            everySuspend { meetingApi.fetchMeetings() } returns NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value)
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

        internal fun withCreateNewMeetingSuccess(meeting: CreateMeeting, response: CreateMeetingResponse) = apply {
            everySuspend {
                meetingApi.createNewMeeting(meetingMapper.fromModelToApi(meeting)) } returns NetworkResponse.Success(
                value = response,
                headers = mapOf(),
                httpCode = HttpStatusCode.Created.value
            )
        }

        internal fun withCreateNewMeetingFailure(meeting: CreateMeeting) = apply {
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

        internal fun arrange() = this to MeetingDataSource(
            selfUserId = selfUserId,
            meetingDAO = meetingDao,
            meetingApi = meetingApi,
            persistConversations = persistConversations,
            mlsConversationRepository = mlsConversationRepository,
            pendingActionsRepository = pendingActionsRepository,
            conversationMapper = conversationMapper,
            idMapper = idMapper,
        )
    }

    private val CREATE_MEETING = CreateMeeting(
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

    private fun createMeetingResponse(
        protocol: ConvProtocol = ConvProtocol.MLS,
        groupId: String? = "group-id",
        epoch: ULong? = 0UL,
        recurrence: MeetingRecurrenceDTO? = MeetingRecurrenceDTO(
            frequency = MeetingFrequencyDTO.WEEKLY,
            interval = 1L,
            until = Instant.parse("2026-12-01T00:00:00Z")
        ),
        conversationId: ConversationId = ConversationId("conversation1", "domain")
    ) = CreateMeetingResponse(
        meetingId = NetworkMeetingId("meeting1", "domain"),
        conversationId = conversationId,
        creatorId = UserId("user1", "domain"),
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
        )
    )
}
