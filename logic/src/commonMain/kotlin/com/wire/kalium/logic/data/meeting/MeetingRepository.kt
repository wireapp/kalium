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

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.meeting.CreateMeetingResponse
import com.wire.kalium.network.api.authenticated.meeting.toMeetingDTO
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.persistence.dao.meeting.MeetingDao
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.util.DateTimeUtil.asStartOfDay
import com.wire.kalium.util.DateTimeUtil.currentInstant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

internal interface MeetingRepository {
    suspend fun fetchAndPersistMeetings(
        generateOccurrencesFrom: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil()
    ): Either<CoreFailure, List<Meeting>>

    suspend fun syncMeetingOccurrences(
        removeOlderThan: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil()
    ): Either<CoreFailure, Unit>

    suspend fun observeMeetingOccurrence(occurrenceId: String): Flow<MeetingOccurrence?>

    suspend fun getPaginatedMeetingOccurrences(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        from: Instant = currentInstant().asStartOfDay(),
    ): Flow<PagingData<MeetingOccurrence>>

    suspend fun deleteMeeting(meetingId: MeetingId): Either<CoreFailure, Unit>

    suspend fun createNewMeeting(
        meeting: CreateMeeting,
        generateOccurrencesFrom: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil(),
        transactionContext: CryptoTransactionContext,
    ): Either<CoreFailure, MLSAdditionResult>
}

@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
internal class MeetingDataSource(
    private val selfUserId: UserId,
    private val meetingDAO: MeetingDao,
    private val meetingApi: MeetingApi,
    private val persistConversations: PersistConversationsUseCase,
    private val mlsConversationRepository: MLSConversationRepository,
    private val pendingActionsRepository: PendingActionsRepository,
    private val meetingMapper: MeetingMapper = MapperProvider.meetingMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : MeetingRepository {
    override suspend fun fetchAndPersistMeetings(
        generateOccurrencesFrom: Instant,
        generateOccurrencesUntil: Instant
    ): Either<CoreFailure, List<Meeting>> =
        wrapApiRequest {
            meetingApi.fetchMeetings()
        }.flatMap { meetings ->
            wrapStorageRequest {
                meetings.mapNotNull { meetingMapper.fromApiToDao(it) }
                    .also { meetingsToPersist ->
                        if (meetingsToPersist.isNotEmpty()) {
                            meetingDAO.upsertMeetings(
                                meetings = meetingsToPersist,
                                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
                            )
                        }
                    }
                    .map { meetingMapper.fromDaoToModel(it) }
            }
        }

    override suspend fun syncMeetingOccurrences(
        removeOlderThan: Instant,
        generateOccurrencesUntil: Instant
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        meetingDAO.removeOutdatedMeetings(removeOlderThan)
        meetingDAO.insertMissingOccurrences(GenerationLimit.Window(removeOlderThan, generateOccurrencesUntil))
    }

    override suspend fun observeMeetingOccurrence(occurrenceId: String): Flow<MeetingOccurrence?> =
        meetingDAO.getMeetingOccurrenceDetailsFlow(occurrenceId)
            .map { it?.let(meetingMapper::fromDaoToModel) }
            .distinctUntilChanged()

    override suspend fun getPaginatedMeetingOccurrences(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        from: Instant,
    ) = meetingDAO.getPaginatedMeetingOccurrenceDetails(
        pagingConfig = pagingConfig,
        startingOffset = startingOffset,
        from = from,
    ).pagingDataFlow.map { pagingData -> pagingData.map(meetingMapper::fromDaoToModel) }

    override suspend fun deleteMeeting(meetingId: MeetingId): Either<CoreFailure, Unit> = withContext(NonCancellable) {
        wrapApiRequest {
            meetingApi.deleteMeeting(meetingId.toApi())
        }.flatMap {
            wrapStorageRequest {
                meetingDAO.deleteMeeting(meetingId.toDao())
            }
        }
    }

    override suspend fun createNewMeeting(
        meeting: CreateMeeting,
        generateOccurrencesFrom: Instant,
        generateOccurrencesUntil: Instant,
        transactionContext: CryptoTransactionContext,
    ): Either<CoreFailure, MLSAdditionResult> = withContext(NonCancellable) {
        wrapApiRequest {
            meetingApi.createNewMeeting(request = meetingMapper.fromModelToApi(meeting))
        }.flatMap { response ->
            response.persist(
                transactionContext = transactionContext,
                otherParticipants = meeting.otherParticipants,
                generateOccurrencesFrom = generateOccurrencesFrom,
                generateOccurrencesUntil = generateOccurrencesUntil
            )
        }
    }

    private suspend fun CreateMeetingResponse.persist(
        transactionContext: CryptoTransactionContext,
        otherParticipants: List<UserId>,
        generateOccurrencesFrom: Instant,
        generateOccurrencesUntil: Instant,
    ): Either<CoreFailure, MLSAdditionResult> = when (val meetingEntity = meetingMapper.fromApiToDao(toMeetingDTO())) {
        null -> Either.Right(MLSAdditionResult.Empty)
        else -> persistConversations(
            transactionContext = transactionContext,
            conversations = listOf(conversation),
            invalidateMembers = true,
            reason = ConversationSyncReason.Other,
        ).flatMap {
            wrapStorageRequest {
                meetingDAO.upsertMeetings(
                    meetings = listOf(meetingEntity),
                    generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
                )
            }.flatMap {
                establishMLSGroupIfNeeded(transactionContext = transactionContext, otherParticipants = otherParticipants)
                    .mapLeft { EstablishMLSFailure(conversationId = conversation.id.toModel()) }
            }
        }
    }

    private suspend fun CreateMeetingResponse.establishMLSGroupIfNeeded(
        transactionContext: CryptoTransactionContext,
        otherParticipants: List<UserId>,
    ): Either<CoreFailure, MLSAdditionResult> = when {
        conversation.protocol == ConvProtocol.PROTEUS // non MLS-capable protocol, no need to establish MLS
                || conversation.groupId == null // no group ID, cannot establish MLS
                || conversation.epoch != 0UL -> // not the first epoch, so already established MLS, no need to do it again
            Either.Right(MLSAdditionResult.Empty)

        else -> transactionContext.wrapInMLSContext {
            mlsConversationRepository.establishMLSGroup(
                mlsContext = it,
                groupID = idMapper.fromGroupIDEntity(conversation.groupId!!),
                members = otherParticipants + selfUserId,
                publicKeys = conversationMapper.fromApiModel(conversation.publicKeys),
                allowSkippingUsersWithoutKeyPackages = true
            ).onFailure {
                if (it.isMLSRetryableError()) {
                    pendingActionsRepository.enqueuePendingMLSGroupJoin(conversationId = conversation.id.toModel())
                }
            }
        }
    }

    private fun CoreFailure.isMLSRetryableError() = when (this) {
        is NetworkFailure.FederatedBackendFailure.RetryableFailure,
        is CoreFailure.MissingKeyPackages,
        is MLSFailure.MessageRejected -> true

        else -> false
    }

    data class EstablishMLSFailure(val conversationId: ConversationId) : CoreFailure.FeatureFailure()
}

private const val OCCURRENCE_GENERATION_WINDOW_DAYS = 90
private const val OUTDATED_MEETING_RETENTION_DAYS = 30
private fun occurrenceGenerationUntil() = currentInstant().asStartOfDay().plus((OCCURRENCE_GENERATION_WINDOW_DAYS + 1).days)
private fun occurrenceOutdatedThreshold() = currentInstant().asStartOfDay().minus(OUTDATED_MEETING_RETENTION_DAYS.days)
