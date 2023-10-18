/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

internal interface SlowSyncWorker {

    /**
     * Performs all [SlowSyncStep] in the correct order,
     * emits the current ongoing step.
     */
    suspend fun slowSyncStepsFlow(): Flow<SlowSyncStep>
}

@Suppress("LongParameterList")
internal class SlowSyncWorkerImpl(
    private val eventRepository: EventRepository,
    private val syncSelfUser: SyncSelfUserUseCase,
    private val syncFeatureConfigs: SyncFeatureConfigsUseCase,
    private val updateSupportedProtocols: UpdateSupportedProtocolsUseCase,
    private val syncConversations: SyncConversationsUseCase,
    private val syncConnections: SyncConnectionsUseCase,
    private val syncSelfTeam: SyncSelfTeamUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val joinMLSConversations: JoinExistingMLSConversationsUseCase,
    private val oneOnOneResolver: OneOnOneResolver,
) : SlowSyncWorker {

    private val logger = kaliumLogger.withFeatureId(SYNC)

    override suspend fun slowSyncStepsFlow(): Flow<SlowSyncStep> = flow {

        suspend fun Either<CoreFailure, Unit>.continueWithStep(
            slowSyncStep: SlowSyncStep,
            step: suspend () -> Either<CoreFailure, Unit>
        ) = flatMap { performStep(slowSyncStep, step) }

        logger.d("Starting SlowSync")

        val lastProcessedEventIdToSaveOnSuccess = getLastProcessedEventIdToSaveOnSuccess()

        performStep(SlowSyncStep.SELF_USER, syncSelfUser::invoke)
            .continueWithStep(SlowSyncStep.FEATURE_FLAGS, syncFeatureConfigs::invoke)
            .continueWithStep(SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS) { updateSupportedProtocols.invoke().map { } }
            .continueWithStep(SlowSyncStep.CONVERSATIONS, syncConversations::invoke)
            .continueWithStep(SlowSyncStep.CONNECTIONS, syncConnections::invoke)
            .continueWithStep(SlowSyncStep.SELF_TEAM, syncSelfTeam::invoke)
            .continueWithStep(SlowSyncStep.CONTACTS, syncContacts::invoke)
            .continueWithStep(SlowSyncStep.JOINING_MLS_CONVERSATIONS, joinMLSConversations::invoke)
            .continueWithStep(SlowSyncStep.RESOLVE_ONE_ON_ONE_PROTOCOLS, oneOnOneResolver::resolveAllOneOnOneConversations)
            .flatMap {
                saveLastProcessedEventIdIfNeeded(lastProcessedEventIdToSaveOnSuccess)
            }
            .onFailure {
                throw KaliumSyncException("Failure during SlowSync", it)
            }
    }

    private suspend fun saveLastProcessedEventIdIfNeeded(lastProcessedEventIdToSaveOnSuccess: String?) =
        if (lastProcessedEventIdToSaveOnSuccess != null) {
            kaliumLogger.i("Saving last processed event ID to complete SlowSync: $lastProcessedEventIdToSaveOnSuccess")
            eventRepository.updateLastProcessedEventId(lastProcessedEventIdToSaveOnSuccess)
        } else {
            kaliumLogger.i("Skipping saving last processed event ID to complete SlowSync")
            Either.Right(Unit)
        }

    private suspend fun getLastProcessedEventIdToSaveOnSuccess(): String? {
        val hasLastEventId = eventRepository.lastProcessedEventId().isRight()
        val lastProcessedEventIdToSaveOnSuccess = if (hasLastEventId) {
            kaliumLogger.i("Last processed event ID already exists, skipping fetch")
            null
        } else {
            kaliumLogger.i("Last processed event ID does not exist, fetching most recent event ID from remote")
            eventRepository.fetchMostRecentEventId().onFailure {
                throw KaliumSyncException("Failure during SlowSync. Unable to fetch most recent event ID", it)
            }.nullableFold({ null }, { it })
        }
        return lastProcessedEventIdToSaveOnSuccess
    }

    private suspend fun FlowCollector<SlowSyncStep>.performStep(
        slowSyncStep: SlowSyncStep,
        step: suspend () -> Either<CoreFailure, Unit>
    ): Either<CoreFailure, Unit> {
        // Check for cancellation
        currentCoroutineContext().ensureActive()

        emit(slowSyncStep)
        return step()
    }
}
