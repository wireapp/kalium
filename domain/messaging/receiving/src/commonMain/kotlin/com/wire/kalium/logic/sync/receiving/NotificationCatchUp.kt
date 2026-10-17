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
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** How a bounded catch-up ended. Stored events are processed in every case, but only [LIVE] caught up. */
enum class CatchUpOutcome {
    /** The socket opened and the pending pages fetched once it was open are stored. */
    LIVE,

    /**
     * The catch-up budget ran out before the socket reached live. Events the socket received may have had
     * no push; the host should show a generic alert.
     */
    BUDGET,

    /**
     * The socket or its paging failed. As in the app's incremental sync, catching up needs the socket;
     * later events stay on the backend for the next attempt.
     */
    FAILED,

    /**
     * The backend dropped events for this client. Nothing was acknowledged, so the app's next
     * connection receives the same signal and recovers with slow sync.
     */
    NEEDS_SLOW_SYNC,
}

/**
 * Bounded legacy incremental sync for a short-lived process, like the Android app's one-shot sync: open
 * the WebSocket, store the pending REST pages fetched once it is open, then close it at live. Pages are
 * fetched only after the socket opens, so no event falls between a page and the socket. The backend sends
 * no push for events it delivers over the socket, so the socket stays open only for catch-up. A frame it
 * delivered but had not stored when it closed got no push either; the next sync's paging stores it
 * without an alert (ADR 11). The host must hold the account and must not run other sync on it.
 */
class NotificationCatchUp(
    private val ingestion: EventIngestion,
    private val closeWebSockets: suspend () -> Unit,
) {
    private val logger = kaliumLogger.withTextTag("NotificationCatchUp")

    /** A [consumable] client catches up on its consumable stream instead; see [consume]. */
    suspend fun run(budget: Duration, consumable: Boolean = false): CatchUpOutcome =
        if (consumable) consume(budget) else catchUp(budget)

    private suspend fun catchUp(budget: Duration): CatchUpOutcome {
        val connected = try {
            withTimeoutOrNull(budget) { connect() }
        } finally {
            // Leaving the socket open would hide later events from their pushes.
            withContext(NonCancellable) { closeWebSockets() }
        }
        val outcome = when (connected) {
            null -> CatchUpOutcome.BUDGET
            true -> CatchUpOutcome.LIVE
            false -> CatchUpOutcome.FAILED
        }
        logger.i("Catch-up finished: $outcome")
        return outcome
    }

    /**
     * Stores and acknowledges each event on this session until the backend confirms its marker.
     * Later events stay unacknowledged and are delivered again; there is no REST stream to page.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Suppress("TooGenericExceptionCaught") // Any connection failure leaves stored events to process.
    private suspend fun consume(budget: Duration): CatchUpOutcome {
        val outcome = try {
            withTimeoutOrNull(budget) {
                val connection = ingestion.consumable(
                    marker = Uuid.random().toString(),
                    strict = true,
                    // Recovery needs slow sync, which only the app can run.
                    onMissedNotification = { throw MissedNotifications() },
                )
                when (connection) {
                    is Either.Left -> CatchUpOutcome.FAILED
                    is Either.Right -> {
                        connection.value.first { it == IngestionPhase.READY }
                        CatchUpOutcome.LIVE
                    }
                }
            } ?: CatchUpOutcome.BUDGET
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: MissedNotifications) {
            CatchUpOutcome.NEEDS_SLOW_SYNC
        } catch (failure: Throwable) {
            logger.w("Consumable catch-up failed: ${failure::class.simpleName}")
            CatchUpOutcome.FAILED
        } finally {
            withContext(NonCancellable) { closeWebSockets() }
        }
        logger.i("Consumable catch-up finished: $outcome")
        return outcome
    }

    private class MissedNotifications : RuntimeException("Notifications were missed")

    /** True once pending pages are stored with the socket open; false if the socket failed. */
    @Suppress("TooGenericExceptionCaught") // Any socket failure fails the catch-up.
    private suspend fun connect(): Boolean = try {
        when (val connection = ingestion.legacy()) {
            is Either.Left -> false
            is Either.Right -> connection.value.first { it == IngestionPhase.READY } == IngestionPhase.READY
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        logger.w("Catch-up socket failed: ${failure::class.simpleName}")
        false
    }
}
