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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What a [LiveHold] returned: the work's [value], how the hold [started] and how it [closed]:
 * [CatchUpOutcome.LIVE] unless the socket never went live or ended on its own while the work ran.
 */
data class HeldResult<T>(val value: T, val started: CatchUpOutcome, val closed: CatchUpOutcome)

/**
 * Keeps a short-lived process's account live while it works (ADR 12), for example while a share
 * extension sends. The WebSocket stays open and stores the events it receives while the work runs; the
 * backend sends no push for them, so the holder must process them and present what they owe before it
 * releases the account. As in the app's incremental sync, the work needs the socket: pages are fetched only
 * once it is open, so no event falls between a page and the socket, and when it does not reach live the
 * work learns so and must not send. Like the Android app's one-shot sync, nothing is paged after the socket
 * closes: a frame it delivered but had not stored got no push and is not alerted until the next sync
 * stores it (ADR 11). The host must hold the account and must not run other sync on it.
 */
class LiveHold(
    private val ingestion: EventIngestion,
    private val closeWebSockets: suspend () -> Unit,
) {
    private val logger = kaliumLogger.withTextTag("LiveHold")

    /**
     * Connects within [connectBudget], then runs [block] with how the hold started: [CatchUpOutcome.LIVE]
     * with the socket open, or why it did not reach live. Afterwards the socket is closed and joined.
     */
    suspend fun <T> run(
        connectBudget: Duration,
        consumable: Boolean,
        block: suspend (started: CatchUpOutcome) -> T,
    ): HeldResult<T> = coroutineScope {
        val opened = CompletableDeferred<CatchUpOutcome>()
        // Why the socket ended on its own; it can fail after it reached live.
        var ended: CatchUpOutcome? = null
        val socket = launch { listen(consumable, opened).also { ended = it }.let(opened::complete) }
        var socketOpen = true
        val closeSocket: suspend () -> Unit = {
            if (socketOpen) withContext(NonCancellable) {
                socketOpen = false
                socket.cancelAndJoin()
                closeWebSockets()
            }
        }
        val (started, value) = try {
            val started = start(withTimeoutOrNull(connectBudget) { opened.await() }, closeSocket)
            started to block(started)
        } finally {
            closeSocket()
        }
        val closed = if (started == CatchUpOutcome.LIVE) ended ?: CatchUpOutcome.LIVE else started
        logger.i("Hold started $started and closed $closed")
        HeldResult(value, started, closed)
    }

    /** There is no REST-only start: without a live socket the work runs knowing it is not live. */
    private suspend fun start(connected: CatchUpOutcome?, closeSocket: suspend () -> Unit): CatchUpOutcome {
        if (connected != CatchUpOutcome.LIVE) closeSocket()
        return connected ?: CatchUpOutcome.BUDGET
    }

    /** Collects the socket until it ends, completing [opened] once it is live; returns why it ended otherwise. */
    @OptIn(ExperimentalUuidApi::class)
    @Suppress("TooGenericExceptionCaught") // Any socket failure means the hold is not live.
    private suspend fun listen(consumable: Boolean, opened: CompletableDeferred<CatchUpOutcome>): CatchUpOutcome = try {
        val connection = if (consumable) {
            ingestion.consumable(
                marker = Uuid.random().toString(),
                strict = true,
                // Recovery needs slow sync, which only the app can run.
                onMissedNotification = { throw MissedNotifications() },
            )
        } else ingestion.legacy()
        when (connection) {
            is Either.Left -> CatchUpOutcome.FAILED
            is Either.Right -> {
                connection.value.collect { if (it == IngestionPhase.READY) opened.complete(CatchUpOutcome.LIVE) }
                CatchUpOutcome.FAILED
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: MissedNotifications) {
        CatchUpOutcome.NEEDS_SLOW_SYNC
    } catch (failure: Throwable) {
        logger.w("Hold socket failed: ${failure::class.simpleName}")
        CatchUpOutcome.FAILED
    }

    private class MissedNotifications : RuntimeException("Notifications were missed")
}
