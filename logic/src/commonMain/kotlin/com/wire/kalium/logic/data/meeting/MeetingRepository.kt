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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
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
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingResponse
import com.wire.kalium.network.api.authenticated.meeting.toMeetingDTO
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.persistence.dao.meeting.MeetingDao
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.util.DateTimeUtil.asStartOfDay
import com.wire.kalium.util.DateTimeUtil.currentInstant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

internal interface MeetingRepository {
    suspend fun fetchAndPersistMeetings(
        generateOccurrencesFrom: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil()
    ): Either<CoreFailure, List<Meeting>>

    suspend fun fetchAndPersistMeeting(
        meetingId: MeetingId,
        generateOccurrencesFrom: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil()
    ): Either<CoreFailure, Meeting>

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

    suspend fun deleteMeetingLocally(meetingId: MeetingId): Either<StorageFailure, Unit>

    suspend fun createNewMeeting(
        meeting: UpsertMeeting,
        generateOccurrencesFrom: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil(),
        transactionContext: CryptoTransactionContext,
    ): Either<CoreFailure, MLSAdditionResult>

    suspend fun updateMeeting(
        meetingId: MeetingId,
        meeting: UpsertMeeting,
        generateOccurrencesFrom: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil(),
        transactionContext: CryptoTransactionContext,
    ): Either<CoreFailure, MLSAdditionResult>

    suspend fun getNextMeetingOccurrence(
        meetingId: MeetingId,
        from: Instant = currentInstant()
    ): Either<StorageFailure, MeetingOccurrence>
}

@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
internal class MeetingDataSource(
    private val selfUserId: UserId,
    private val meetingDAO: MeetingDao,
    private val meetingApi: MeetingApi,
    private val persistConversations: PersistConversationsUseCase,
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationRepository: ConversationRepository,
    private val pendingActionsRepository: PendingActionsRepository,
    private val userRepository: UserRepository,
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
                        meetingDAO.upsertMeetings(
                            meetings = meetingsToPersist,
                            generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil),
                            removeMeetingsAbsentFromUpsertList = true,
                        )
                    }
                    .map { meetingMapper.fromDaoToModel(it) }
            }
        }

    override suspend fun fetchAndPersistMeeting(
        meetingId: MeetingId,
        generateOccurrencesFrom: Instant,
        generateOccurrencesUntil: Instant
    ): Either<CoreFailure, Meeting> =
        wrapApiRequest {
            meetingApi.fetchMeeting(meetingId.toApi())
        }.flatMap { meetingDTO ->
            meetingMapper.fromApiToDao(meetingDTO)?.let { meetingEntity ->
                // in case the creator is not yet known, probably deleted, we insert an incomplete user to avoid
                // foreign key constraint violation and try to fetch the user details from the server if possible
                userRepository.insertOrIgnoreIncompleteUsers(listOf(meetingEntity.creatorId.toModel()))
                userRepository.fetchUsersIfUnknownByIds(setOf(meetingEntity.creatorId.toModel()))
                wrapStorageRequest {
                    meetingDAO.upsertMeetings(
                        meetings = listOf(meetingEntity),
                        generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
                    )
                    meetingMapper.fromDaoToModel(meetingEntity)
                }
            } ?: Either.Left(MeetingNotSupportedFailure)
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
            deleteMeetingLocally(meetingId)
        }
    }

    override suspend fun deleteMeetingLocally(meetingId: MeetingId): Either<StorageFailure, Unit> = wrapStorageRequest {
        meetingDAO.deleteMeeting(meetingId.toDao())
    }

    override suspend fun getNextMeetingOccurrence(
        meetingId: MeetingId,
        from: Instant
    ): Either<StorageFailure, MeetingOccurrence> = wrapStorageRequest {
        meetingDAO.getNextMeetingOccurrenceDetailsId(meetingId.toDao(), from)?.let { occurrenceId ->
            meetingDAO.getMeetingOccurrenceDetailsFlow(occurrenceId).firstOrNull()?.let(meetingMapper::fromDaoToModel)
        }
    }

    override suspend fun createNewMeeting(
        meeting: UpsertMeeting,
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

    override suspend fun updateMeeting(
        meetingId: MeetingId,
        meeting: UpsertMeeting,
        generateOccurrencesFrom: Instant,
        generateOccurrencesUntil: Instant,
        transactionContext: CryptoTransactionContext
    ): Either<CoreFailure, MLSAdditionResult> =
        wrapStorageRequest {
            meetingDAO.getMeeting(meetingId.toDao())
        }.flatMap { meetingEntity ->
            conversationRepository.getConversationById(conversationId = meetingEntity.conversationId.toModel())
        }.flatMap { conversation ->
            updateMembers(
                conversation = conversation,
                upsertMeeting = meeting,
                transactionContext = transactionContext
            ).flatMap { mlsAdditionResult ->
                wrapApiRequest {
                    meetingApi.updateMeeting(meetingId = meetingId.toApi(), request = meetingMapper.fromModelToApi(meeting))
                }.flatMap { response ->
                    response.updateConversationName(conversation = conversation, upsertMeeting = meeting) { response ->
                        response.persist(
                            transactionContext = transactionContext,
                            otherParticipants = meeting.otherParticipants,
                            generateOccurrencesFrom = generateOccurrencesFrom,
                            generateOccurrencesUntil = generateOccurrencesUntil
                        )
                    }
                }.map { mlsEstablishmentResult ->
                    if (mlsEstablishmentResult == MLSAdditionResult.Empty) mlsAdditionResult else mlsEstablishmentResult
                }
            }
        }

    private suspend fun updateMembers(
        conversation: Conversation,
        upsertMeeting: UpsertMeeting,
        transactionContext: CryptoTransactionContext,
    ): Either<CoreFailure, MLSAdditionResult> {
        val mlsCapableProtocol = conversation.protocol as? Conversation.ProtocolInfo.MLSCapable
        return if (mlsCapableProtocol?.groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED) {
            conversationRepository.getConversationMembers(conversation.id)
                .map { it.filterNot { it == selfUserId } } // exclude self user from current members
                .flatMap { currentMembers ->
                    updateMembers(
                        mlsCapableProtocol = mlsCapableProtocol,
                        membersToAdd = upsertMeeting.otherParticipants.filterNot { it in currentMembers },
                        membersToRemove = currentMembers.filterNot { it in upsertMeeting.otherParticipants },
                        transactionContext = transactionContext
                    )
                }
        } else {
            Either.Right(MLSAdditionResult.Empty) // no MLS-capable protocol or not established, so no need to update members
        }
    }

    private suspend fun updateMembers(
        mlsCapableProtocol: Conversation.ProtocolInfo.MLSCapable,
        membersToAdd: List<UserId>,
        membersToRemove: List<UserId>,
        transactionContext: CryptoTransactionContext,
    ): Either<CoreFailure, MLSAdditionResult> =
        if ((membersToAdd + membersToRemove).isNotEmpty()) {
            transactionContext.wrapInMLSContext { mlsContext ->
                when {
                    membersToRemove.isNotEmpty() -> mlsConversationRepository.removeMembersFromMLSGroup(
                        mlsContext = mlsContext,
                        groupID = mlsCapableProtocol.groupId,
                        userIdList = membersToRemove,
                    )

                    else -> Either.Right(Unit)
                }.flatMap {
                    when {
                        membersToAdd.isNotEmpty() -> mlsConversationRepository.addMemberToMLSGroup(
                            mlsContext = mlsContext,
                            groupID = mlsCapableProtocol.groupId,
                            userIdList = membersToAdd,
                            cipherSuite = mlsCapableProtocol.cipherSuite,
                            allowPartialMemberList = true
                        )

                        else -> Either.Right(MLSAdditionResult.Empty)
                    }
                }
            }
        } else {
            // no members to add and remove, or no group ID or cipher suite tag, so nothing to do
            Either.Right(MLSAdditionResult.Empty)
        }

    private suspend fun <T> UpsertMeetingResponse.updateConversationName(
        conversation: Conversation,
        upsertMeeting: UpsertMeeting,
        action: suspend (UpsertMeetingResponse) -> Either<CoreFailure, T>,
    ): Either<CoreFailure, T> {
        val changeConversationNameResult = when {
            conversation.name == upsertMeeting.title -> Either.Right(ConversationRenameResponse.Unchanged) // no need to execute the change
            else -> conversationRepository.changeConversationName(conversationId = conversation.id, conversationName = upsertMeeting.title)
        }
        val updatedResponse = changeConversationNameResult.fold(
            { this },
            { this.copy(conversation = this.conversation.copy(name = upsertMeeting.title)) }
        )
        return action(updatedResponse).flatMap { actionResult ->
            changeConversationNameResult.fold(
                { Either.Left(UpdateConversationNameFailure(conversationId = conversation.id, reason = it)) },
                { Either.Right(actionResult) }
            )
        }
    }

    private suspend fun UpsertMeetingResponse.persist(
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
            }
        }
    }

    private suspend fun UpsertMeetingResponse.establishMLSGroupIfNeeded(
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
            }.mapLeft {
                EstablishMLSFailure(conversationId = conversation.id.toModel(), reason = it)
            }
        }
    }

    private fun CoreFailure.isMLSRetryableError() = when (this) {
        is NetworkFailure.FederatedBackendFailure.RetryableFailure,
        is CoreFailure.MissingKeyPackages,
        is MLSFailure.MessageRejected -> true

        else -> false
    }

    data class EstablishMLSFailure(val conversationId: ConversationId, val reason: CoreFailure) : CoreFailure.FeatureFailure()
    data class UpdateConversationNameFailure(val conversationId: ConversationId, val reason: CoreFailure) : CoreFailure.FeatureFailure()
    data object MeetingNotSupportedFailure : CoreFailure.FeatureFailure()
}

private const val OCCURRENCE_GENERATION_WINDOW_DAYS = 90
private const val OUTDATED_MEETING_RETENTION_DAYS = 30
private fun occurrenceGenerationUntil() = currentInstant().asStartOfDay().plus((OCCURRENCE_GENERATION_WINDOW_DAYS + 1).days)
private fun occurrenceOutdatedThreshold() = currentInstant().asStartOfDay().minus(OUTDATED_MEETING_RETENTION_DAYS.days)
