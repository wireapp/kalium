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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.receiver.conversation.message.DurableMLSReceiveContext
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach

/**
 * Gathers and processes IncrementalSync events.
 */
internal interface IncrementalSyncWorker {
    /**
     * Upon collection, will start collecting and processing events,
     * emitting the source of current events.
     */
    suspend fun processEventsFlow(): Flow<EventSource>
}

@Suppress("LongParameterList") // Dependencies belong to the existing account-owned sync worker.
internal class IncrementalSyncWorkerImpl(
    private val eventGatherer: EventGatherer,
    private val eventProcessor: EventProcessor,
    private val transactionProvider: CryptoTransactionProvider,
    private val databaseBuilder: UserDatabaseBuilder,
    private val eventRepository: EventRepository,
    logger: KaliumLogger = kaliumLogger,
    private val consumePendingAccountInvalidation: suspend () -> Boolean = { false },
    private val recordMLSReceipts: (suspend (CryptoTransactionContext, List<EventEnvelope>) -> Unit)? = null,
    private val werePayloadEffectsProcessed: (suspend (CryptoTransactionContext, EventEnvelope) -> Boolean)? = null,
    private val acknowledgeMLSRecovery: (suspend (List<String>) -> Unit)? = null,
) : IncrementalSyncWorker {

    private val logger = logger.withFeatureId(SYNC)

    private suspend fun processEnvelope(
        context: CryptoTransactionContext,
        envelope: EventEnvelope,
    ): Either<CoreFailure, String?> {
        val effectsProcessed = context is DurableMLSReceiveContext && werePayloadEffectsProcessed != null &&
            !eventProcessor.disableEventProcessing && envelope.event is Event.Conversation &&
            werePayloadEffectsProcessed.invoke(context, envelope)
        // Conversation replay can overwrite canonical state restored by a later recovery.
        // Other event families retain normal replay: e.g. connections schedule resolution after Live,
        // and a receipt cannot prove that another scope completed that work before a restart.
        if (effectsProcessed && envelope.event !is Event.Conversation.NewMessage) return Either.Right(envelope.event.id)
        // SQL effects are committed, but an outer Proteus rollback still requires native decryption.
        val eventContext = if (effectsProcessed) DurableMLSReceiveContext(context, effectsAlreadyPersisted = true) else context
        return eventProcessor.processEvent(eventContext, envelope)
    }

    private fun queueEventId(context: CryptoTransactionContext, envelope: EventEnvelope, handledId: String): String =
        if (context is DurableMLSReceiveContext) envelope.payloadIdentity?.eventId ?: handledId else handledId

    private suspend fun processBatch(
        context: CryptoTransactionContext,
        envelopes: List<EventEnvelope>,
        processed: MutableList<EventEnvelope>,
    ): Either<CoreFailure, MutableList<String>> = if (context is DurableMLSReceiveContext) {
        envelopes.foldToEitherWhileRight(mutableListOf<String>()) { envelope, acc ->
            processEnvelope(context, envelope).map { eventId ->
                if (eventId != null) {
                    processed += envelope
                    acc += queueEventId(context, envelope, eventId)
                }
                acc
            }
        }
    } else {
        // Preserve the legacy receive path: process the entire batch before collecting failures.
        // Durable Apple recovery must stop at the first failure.
        envelopes.map { envelope -> eventProcessor.processEvent(context, envelope) }
            .foldToEitherWhileRight(mutableListOf<String>()) { eventEither, acc ->
                eventEither.map { eventId ->
                    eventId?.let(acc::add)
                    acc
                }
            }
    }

    override suspend fun processEventsFlow(): Flow<EventSource> = channelFlow {
        // We start as PENDING
        send(EventSource.PENDING)
        // Removed credentials may never connect; the existing logout owner runs first.
        if (consumePendingAccountInvalidation()) kotlinx.coroutines.awaitCancellation()

        kaliumLogger.d("$TAG gatherEvents starting...")
        eventGatherer.gatherEvents()
            // If we ever become Up-To-Date, move to LIVE
            .onEach { eventStreamData ->
                if (eventStreamData is EventStreamData.IsUpToDate) {
                    send(EventSource.LIVE) // We are LIVE!!!!!!
                }
            }
            .filterIsInstance<EventStreamData.NewEvents>()
            .collect { streamData ->
                withContext(NonCancellable) {
                    val envelopes = streamData.eventList
                    kaliumLogger.d("$TAG Received ${envelopes.size} events to process")
                    transactionProvider.transaction("processEvents") { transactionContext ->
                        val context = if (recordMLSReceipts != null && acknowledgeMLSRecovery != null) {
                            DurableMLSReceiveContext(transactionContext)
                        } else transactionContext
                        val processed = mutableListOf<EventEnvelope>()
                        databaseBuilder.dbInvalidationController.runMuted {
                            processBatch(context, envelopes, processed)
                                .flatMap { eventIds ->
                                    eventProcessor.flushPendingSideEffects().map {
                                        recordMLSReceipts?.invoke(context, processed)
                                        eventIds
                                    }
                                }
                                .onFailure { throw KaliumSyncException("Processing failed", it) }
                        }
                    }
                        .onSuccess { eventIds ->
                            if (eventIds.isEmpty()) {
                                logger.i("No events to mark as processed")
                                return@onSuccess
                            }

                            if (acknowledgeMLSRecovery != null) {
                                // The Apple store atomically marks rows and retires recovery receipts.
                                acknowledgeMLSRecovery.invoke(eventIds)
                                logger.i("${eventIds.size} events set as processed")
                            } else {
                                eventRepository.setEventsAsProcessed(eventIds)
                                    .onSuccess { logger.i("${eventIds.size} events set as processed") }
                            }
                        }
                        .onFailure {
                            throw KaliumSyncException("Processing failed", it)
                        }
                }
            }
        logger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
    }.distinctUntilChanged()

    companion object {
        const val TAG = "[IncrementalSyncWorker]"
    }
}
