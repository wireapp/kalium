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

package com.wire.kalium.eventprocessing

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Processes a complete, already selected batch of events. */
@InternalKaliumApi
public interface EventBatchProcessor<Event> {
    public suspend fun processEvents(events: List<Event>)
}

/**
 * Supplies the application-specific operations needed by [DefaultEventBatchProcessor].
 *
 * The fixed result types keep the reusable engine independent from event, crypto-provider,
 * persistence, and repository implementations owned by higher-level modules.
 */
@InternalKaliumApi
public interface EventBatchProcessingRuntime<Event, TransactionContext> {
    public suspend fun transaction(
        block: suspend (TransactionContext) -> Either<CoreFailure, List<String>>
    ): Either<CoreFailure, List<String>>

    public suspend fun runMuted(
        block: suspend () -> Either<CoreFailure, List<String>>
    ): Either<CoreFailure, List<String>>

    public suspend fun processEvent(
        transactionContext: TransactionContext,
        event: Event,
    ): Either<CoreFailure, String?>

    public suspend fun flushPendingSideEffects(): Either<CoreFailure, Unit>

    /** Returns true when the marker write succeeds and false when its current ignored failure occurs. */
    public suspend fun markEventsAsProcessed(eventIds: List<String>): Boolean

    public fun processingException(failure: CoreFailure): Throwable
}

/** Observes processing without coupling the engine to an application logger. */
@InternalKaliumApi
public interface EventBatchProcessingObserver {
    public fun onBatchReceived(eventCount: Int)
    public fun onNoEventsToMarkAsProcessed()
    public fun onEventsMarkedAsProcessed(eventCount: Int)
}

/** Default observer for callers that do not need batch-processing logs. */
@InternalKaliumApi
public object NoOpEventBatchProcessingObserver : EventBatchProcessingObserver {
    override fun onBatchReceived(eventCount: Int): Unit = Unit
    override fun onNoEventsToMarkAsProcessed(): Unit = Unit
    override fun onEventsMarkedAsProcessed(eventCount: Int): Unit = Unit
}

/**
 * Behavior-preserving event batch engine.
 *
 * The whole transaction/process/flush/mark sequence intentionally remains non-cancellable.
 */
@InternalKaliumApi
public class DefaultEventBatchProcessor<Event, TransactionContext>(
    private val runtime: EventBatchProcessingRuntime<Event, TransactionContext>,
    private val observer: EventBatchProcessingObserver = NoOpEventBatchProcessingObserver,
) : EventBatchProcessor<Event> {

    override suspend fun processEvents(events: List<Event>) {
        withContext(NonCancellable) {
            observer.onBatchReceived(events.size)
            runtime.transaction { context ->
                runtime.runMuted {
                    events.map { event -> runtime.processEvent(context, event) }
                        .foldToEitherWhileRight(mutableListOf<String>()) { eventEither, acc ->
                            eventEither.map { eventId ->
                                eventId?.let(acc::add)
                                acc
                            }
                        }
                        .flatMap { eventIds ->
                            runtime.flushPendingSideEffects().map { eventIds }
                        }
                }
            }
                .onSuccess { eventIds ->
                    if (eventIds.isEmpty()) {
                        observer.onNoEventsToMarkAsProcessed()
                        return@onSuccess
                    }

                    if (runtime.markEventsAsProcessed(eventIds)) {
                        observer.onEventsMarkedAsProcessed(eventIds.size)
                    }
                }
                .onFailure { failure ->
                    throw runtime.processingException(failure)
                }
        }
    }
}
