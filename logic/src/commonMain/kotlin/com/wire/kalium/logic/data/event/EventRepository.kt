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

package com.wire.kalium.logic.data.event

import co.touchlab.stately.concurrency.AtomicReference
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.incremental.IncrementalSyncPhase
import com.wire.kalium.logic.sync.receiving.EventIngestion
import com.wire.kalium.logic.sync.receiving.IngestionPhase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.event.EventDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal interface EventRepository {

    /**
     * Performs an acknowledgment of the missed event after performing a slow sync.
     */
    suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit>
    suspend fun liveEvents(): Either<CoreFailure, Flow<IncrementalSyncPhase>>
    suspend fun setEventAsProcessed(eventId: String): Either<StorageFailure, Unit>

    /**
     * Parse events from an external JSON payload
     *
     * @return List of [EventEnvelope]
     */
    fun parseExternalEvents(data: String): List<EventEnvelope>

    /**
     * Retrieves the last saved event ID from the storage.
     *
     * @return an [Either] object representing either a [StorageFailure] or a [String].
     *         - If the retrieval is successful, returns [Either.Right] with the last saved event ID as a [String].
     *         - If there is a failure during retrieval, returns [Either.Left] with a [StorageFailure] object.
     */
    suspend fun lastSavedEventId(): Either<StorageFailure, String>

    /**
     * Clears the last saved event ID.
     *
     * @return An [Either] object representing the result of the operation.
     * The [Either] object contains either a [StorageFailure] if the operation fails, or [Unit] if the operation succeeds.
     */
    suspend fun clearLastSavedEventId(): Either<StorageFailure, Unit>

    /**
     * Updates the last saved event ID.
     *
     * @param eventId The ID of the event to be set as the last saved event ID.
     *
     * @return An [Either] object representing the result of the operation.
     * The [Either] object contains either a [StorageFailure] if the operation fails, or [Unit] if the operation succeeds.
     */
    suspend fun updateLastSavedEventId(eventId: String): Either<StorageFailure, Unit>

    suspend fun fetchMostRecentEventId(): Either<CoreFailure, String>

    /**
     * Fetches the oldest available event ID from remote.
     *
     * @return Either containing a [CoreFailure] or the oldest available event ID as a String.
     */
    suspend fun fetchOldestAvailableEventId(): Either<CoreFailure, String>
    fun observeEvents(): Flow<List<EventEnvelope>>
    suspend fun setEventsAsProcessed(eventIds: List<String>): Either<StorageFailure, Unit>
    fun setUnprocessedEventsBatchLimit(limit: Int?)
}

@Suppress("TooManyFunctions", "LongParameterList")
internal class EventDataSource(
    private val notificationApi: NotificationApi,
    private val metadataDAO: MetadataDAO,
    private val eventDAO: EventDAO,
    private val currentClientId: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val clientRegistrationStorage: ClientRegistrationStorage,
    private val restartSlowSyncProcessForRecovery: RestartSlowSyncProcessForRecoveryUseCase,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
    logger: KaliumLogger
) : EventRepository {

    val logger = logger.withTextTag(TAG)

    private val sentinelMarker = AtomicReference<SentinelMarker>(SentinelMarker.None)
    private val unprocessedEventsBatchLimit = AtomicReference(EventDAO.DEFAULT_UNPROCESSED_EVENTS_LIMIT.toInt())

    override fun setUnprocessedEventsBatchLimit(limit: Int?) {
        val batchLimit = limit?.coerceAtLeast(1) ?: EventDAO.DEFAULT_UNPROCESSED_EVENTS_LIMIT.toInt()
        unprocessedEventsBatchLimit.set(batchLimit)
        logger.i("set unprocessed events batch limit to $batchLimit")
    }

    override fun observeEvents(): Flow<List<EventEnvelope>> {
        var lastEmittedEventId: String? = null
        val batchLimit = unprocessedEventsBatchLimit.get().toLong()
        return eventDAO.observeUnprocessedEvents(batchLimit).transform { eventEntities ->
            logger.d("got ${eventEntities.size} unprocessed events")
            if (eventEntities.isNotEmpty()) {
                logger.d("first unprocessed event ${eventEntities.firstOrNull()?.eventId}")
                logger.d("last unprocessed event ${eventEntities.lastOrNull()?.eventId}")
            }
            logger.d("current last emitted event id: $lastEmittedEventId")

            val emittedEventIndex = eventEntities.indexOfFirst { entity -> entity.eventId == lastEmittedEventId }

            if (emittedEventIndex == -1) {
                emit(eventEntities)
                return@transform
            }
            if (emittedEventIndex != eventEntities.lastIndex) {
                logger.d("filtered out ${emittedEventIndex + 1} events already marked as processed")
                emit(eventEntities.subList(emittedEventIndex + 1, eventEntities.size))
            } else {
                logger.d("no unprocessed events found")
            }
        }
            .onEach { entities ->
                entities.lastOrNull()?.let {
                    lastEmittedEventId = it.eventId
                }
            }
            .map { eventEntities ->
                eventEntities.map { entity ->
                    val payload: List<EventContentDTO>? =
                        entity.payload?.let {
                            KtxSerializer.json.decodeFromString<List<EventContentDTO>>(it)
                        }
                    eventMapper.fromStoredDTO(eventId = entity.eventId, payload = payload, isLive = entity.isLive)
                }
                    .flatten()
            }
    }

    override suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit> {
        logger.d("Handling acknowledgeMissedEvent")
        return currentClientId().fold(
            { it.left() },
            {
                notificationApi.acknowledgeEvents(it.value, sentinelMarker.get().getMarker(), EventMapper.FULL_ACKNOWLEDGE_REQUEST)
                Unit.right()
            }
        )
    }

    // TODO(edge-case): handle Missing notification response (notify user that some messages are missing)
    override suspend fun liveEvents(): Either<CoreFailure, Flow<IncrementalSyncPhase>> =
        currentClientId().flatMap { clientId ->
            val ingestion = EventIngestion(notificationApi, eventDAO, metadataDAO, clientId.value, logger) {
                currentClientId().map { it.value }
            }
            val hasConsumableNotifications = clientRegistrationStorage.observeHasConsumableNotifications().firstOrNull()
            if (hasConsumableNotifications == true) {
                consumeLiveEventsFlow(ingestion)
            } else {
                ingestion.legacy(clearProcessedOnFirstEvent = true).map { it.asSyncPhases() }
            }
        }

    private suspend fun consumeLiveEventsFlow(ingestion: EventIngestion): Either<NetworkFailure, Flow<IncrementalSyncPhase>> {
        sentinelMarker.set(SentinelMarker.Marker(Uuid.random().toString()))
        logger.d("Creating new sentinel marker [${sentinelMarker.get().getMarker()}] for this session.")
        return ingestion.consumable(
            marker = sentinelMarker.get().getMarker(),
            clearProcessedOnFirstEvent = true,
            onCaughtUp = { sentinelMarker.set(SentinelMarker.None) },
            onMissedNotification = {
                acknowledgeMissedEvent()
                restartSlowSyncProcessForRecovery()
            },
        ).map { it.asSyncPhases() }
    }

    private fun Flow<IngestionPhase>.asSyncPhases(): Flow<IncrementalSyncPhase> = map {
        when (it) {
            IngestionPhase.CATCHING_UP -> IncrementalSyncPhase.CatchingUp
            IngestionPhase.READY -> IncrementalSyncPhase.ReadyToProcess
        }
    }

    override fun parseExternalEvents(data: String): List<EventEnvelope> {
        val notificationResponse = Json.decodeFromString<NotificationResponse>(data)
        return notificationResponse.notifications.flatMap {
            eventMapper.fromDTO(it.toEventResponse(), isLive = false)
        }
    }

    override suspend fun lastSavedEventId(): Either<StorageFailure, String> = wrapStorageRequest {
        metadataDAO.valueByKey(LAST_SAVED_EVENT_ID_KEY)
    }

    override suspend fun clearLastSavedEventId(): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.deleteValue(LAST_SAVED_EVENT_ID_KEY)
    }

    override suspend fun fetchMostRecentEventId(): Either<CoreFailure, String> =
        currentClientId()
            .flatMap { currentClientId ->
                wrapApiRequest { notificationApi.mostRecentNotification(currentClientId.value) }
                    .map { it.id }
            }

    override suspend fun updateLastSavedEventId(eventId: String): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.insertValue(eventId, LAST_SAVED_EVENT_ID_KEY)
    }

    override suspend fun setEventAsProcessed(eventId: String): Either<StorageFailure, Unit> = wrapStorageRequest {
        eventDAO.markEventAsProcessed(eventId)
    }

    override suspend fun setEventsAsProcessed(eventIds: List<String>): Either<StorageFailure, Unit> = wrapStorageRequest {
        eventDAO.markEventsAsProcessed(eventIds)
    }

    override suspend fun fetchOldestAvailableEventId(): Either<CoreFailure, String> =
        currentClientId().flatMap { clientId ->
            wrapApiRequest {
                notificationApi.oldestNotification(clientId.value)
            }
        }.map { it.id }

    private companion object {
        // DO NOT CHANGE THE NAME IT IS INTENTIONAL THAT LAST_SAVED_EVENT_ID_KEY VALUE IS last_processed_event_id
        // CHANGE IT AND YOU WILL BE FIRED
        // ALSO KUBAZ WILL THROW A CURSE AT YOUR FAMILY
        // SERIOUSLY, YOUR PHONE WILL AUTOCORRECT EVERYTHING TO "BANANA" FOR A MONTH
        // YOUR COFFEE WILL TASTE LIKE SADNESS AND BROKEN DREAMS
        // LEGACY REASONS - THIS NAME IS SACRED AND MUST NOT BE TOUCHED
        // CHANGING THIS WILL CAUSE THE BUILD SERVERS TO PERSONALLY HUNT YOU DOWN
        // THE LAST PERSON WHO CHANGED THIS IS NOW WORKING AS A MIME IN FRANCE
        // DON'T BE THAT PERSON. JUST DON'T.
        const val LAST_SAVED_EVENT_ID_KEY = "last_processed_event_id"
        const val TAG = "[EventRepository]"
    }
}
