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

package com.wire.kalium.nomaddevice

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

public data class NomadRemoteBackupDebouncedSyncConfig(
    val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    val maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
    val maxAttemptsTotal: Int = DEFAULT_MAX_ATTEMPTS_TOTAL,
    val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    init {
        require(debounceMs > 0) { "debounceMs must be > 0." }
        require(maxWaitMs > 0) { "maxWaitMs must be > 0." }
        require(maxAttemptsTotal > 0) { "maxAttemptsTotal must be > 0." }
        require(retryDelaysMs.size >= (maxAttemptsTotal - 1)) {
            "retryDelaysMs must provide at least maxAttemptsTotal - 1 entries."
        }
        require(retryDelaysMs.all { it > 0 }) { "retryDelaysMs values must be > 0." }
    }

    public companion object {
        public const val DEFAULT_DEBOUNCE_MS: Long = 10_000L
        public const val DEFAULT_MAX_WAIT_MS: Long = 60_000L
        public const val DEFAULT_MAX_ATTEMPTS_TOTAL: Int = 3
        public val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(10_000L, 20_000L)
    }
}

/**
 * Creates a [PersistenceEventHookNotifier] that:
 * 1) writes syncable hook events into remote-backup changelog, and
 * 2) triggers debounced changelog flushing to Nomad.
 */
public fun createDebouncedNomadRemoteBackupChangeLogHookNotifier(
    userStorageProvider: UserStorageProvider,
    nomadAuthenticatedNetworkAccess: NomadAuthenticatedNetworkAccess,
    scope: CoroutineScope,
    config: NomadRemoteBackupDebouncedSyncConfig = NomadRemoteBackupDebouncedSyncConfig(),
    pageSize: Long = DEFAULT_SYNC_PAGE_SIZE,
): PersistenceEventHookNotifier {
    val syncUseCase = SyncNomadRemoteBackupChangeLogUseCase(
        userStorageProvider = userStorageProvider,
        nomadAuthenticatedNetworkAccess = nomadAuthenticatedNetworkAccess,
        pageSize = pageSize
    )
    val changelogHookNotifier = createNomadRemoteBackupChangeLogHookNotifier(userStorageProvider = userStorageProvider)
    val syncController = NomadRemoteBackupChangeLogDebouncedSyncController(
        scope = scope,
        config = config,
        syncUseCase = syncUseCase::invoke,
    )
    return DebouncedNomadRemoteBackupChangeLogHookNotifier(
        delegate = changelogHookNotifier,
        onHookTriggered = syncController::onHookTriggered,
    )
}

internal class DebouncedNomadRemoteBackupChangeLogHookNotifier(
    private val delegate: PersistenceEventHookNotifier,
    private val onHookTriggered: suspend (UserId) -> Unit,
) : PersistenceEventHookNotifier {

    override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
        delegate.onMessagePersisted(message, selfUserId)
        onHookTriggered(selfUserId)
    }

    override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
        delegate.onMessageDeleted(data, selfUserId)
        onHookTriggered(selfUserId)
    }

    override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
        delegate.onReactionPersisted(data, selfUserId)
        onHookTriggered(selfUserId)
    }

    override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
        delegate.onReadReceiptPersisted(data, selfUserId)
        onHookTriggered(selfUserId)
    }

    override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
        delegate.onConversationDeleted(data, selfUserId)
        onHookTriggered(selfUserId)
    }

    override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
        delegate.onConversationCleared(data, selfUserId)
        onHookTriggered(selfUserId)
    }
}

internal class NomadRemoteBackupChangeLogDebouncedSyncController(
    private val scope: CoroutineScope,
    private val config: NomadRemoteBackupDebouncedSyncConfig,
    private val syncUseCase: suspend (UserId) -> Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult>,
    private val nowMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val mutex = Mutex()
    private val userStates = mutableMapOf<UserId, UserSyncState>()

    suspend fun onHookTriggered(selfUserId: UserId) {
        val nowMs = nowMsProvider()
        var shouldSchedule = false
        var delayMs = 0L
        var jobToCancel: Job? = null
        mutex.withLock {
            val state = userStates.getOrPut(selfUserId) { UserSyncState() }
            when {
                state.inFlight -> {
                    state.recordTrigger(nowMs)
                    return@withLock
                }

                state.scheduledKind == ScheduledKind.RETRY -> {
                    return@withLock
                }

                else -> {
                    state.recordTrigger(nowMs)
                    val dueAtMs = minOf(
                        state.lastTriggerMs!! + config.debounceMs,
                        state.firstTriggerMs!! + config.maxWaitMs
                    )
                    delayMs = (dueAtMs - nowMs).coerceAtLeast(0L)
                    shouldSchedule = true
                    jobToCancel = state.scheduledJob
                }
            }
        }
        jobToCancel?.cancel()
        if (shouldSchedule) {
            schedule(selfUserId, ScheduledKind.DEBOUNCE, delayMs)
        }
    }

    private suspend fun schedule(selfUserId: UserId, kind: ScheduledKind, delayMs: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMs)
            onScheduledDue(selfUserId, kind)
        }
        var shouldStart = false
        mutex.withLock {
            val state = userStates[selfUserId]
            if (state == null) {
                job.cancel()
                return@withLock
            }
            state.scheduledJob?.cancel()
            state.scheduledJob = job
            state.scheduledKind = kind
            shouldStart = true
        }
        if (shouldStart) {
            job.start()
        }
    }

    private suspend fun onScheduledDue(selfUserId: UserId, kind: ScheduledKind) {
        var shouldRun = false
        mutex.withLock {
            val state = userStates[selfUserId]
            if (state != null && state.scheduledKind == kind) {
                state.scheduledJob = null
                state.scheduledKind = null
                if (!state.inFlight) {
                    state.inFlight = true
                    state.firstTriggerMs = null
                    state.lastTriggerMs = null
                    state.attemptCount += 1
                    shouldRun = true
                }
            }
        }

        if (!shouldRun) return

        when (flushAllPending(selfUserId)) {
            is Either.Left -> onFlushFailure(selfUserId)
            is Either.Right -> onFlushSuccess(selfUserId)
        }
    }

    private suspend fun flushAllPending(selfUserId: UserId): Either<CoreFailure, Unit> {
        while (true) {
            when (val result = syncUseCase(selfUserId)) {
                is Either.Left -> return result.value.left()
                is Either.Right -> {
                    if (result.value.syncedEntries == 0) return Unit.right()
                }
            }
        }
    }

    private suspend fun onFlushSuccess(selfUserId: UserId) {
        var shouldSchedule = false
        var delayMs = 0L
        mutex.withLock {
            val state = userStates[selfUserId] ?: return
            state.inFlight = false
            state.attemptCount = 0

            val firstTriggerMs = state.firstTriggerMs
            val lastTriggerMs = state.lastTriggerMs
            if (firstTriggerMs != null && lastTriggerMs != null) {
                val nowMs = nowMsProvider()
                val dueAtMs = minOf(
                    lastTriggerMs + config.debounceMs,
                    firstTriggerMs + config.maxWaitMs
                )
                delayMs = (dueAtMs - nowMs).coerceAtLeast(0L)
                shouldSchedule = true
            } else if (state.isIdle()) {
                userStates.remove(selfUserId)
            }
        }
        if (shouldSchedule) {
            schedule(selfUserId, ScheduledKind.DEBOUNCE, delayMs)
        }
    }

    private suspend fun onFlushFailure(selfUserId: UserId) {
        var retryDelayMs: Long? = null
        mutex.withLock {
            val state = userStates[selfUserId] ?: return
            state.inFlight = false
            val hasRemainingAttempts = state.attemptCount < config.maxAttemptsTotal
            if (hasRemainingAttempts) {
                retryDelayMs = config.retryDelaysMs[state.attemptCount - 1]
            } else {
                state.attemptCount = 0
                state.firstTriggerMs = null
                state.lastTriggerMs = null
                if (state.isIdle()) {
                    userStates.remove(selfUserId)
                }
            }
        }
        retryDelayMs?.let { schedule(selfUserId, ScheduledKind.RETRY, it) }
    }

    private data class UserSyncState(
        var firstTriggerMs: Long? = null,
        var lastTriggerMs: Long? = null,
        var inFlight: Boolean = false,
        var attemptCount: Int = 0,
        var scheduledJob: Job? = null,
        var scheduledKind: ScheduledKind? = null,
    ) {
        fun recordTrigger(nowMs: Long) {
            if (firstTriggerMs == null) {
                firstTriggerMs = nowMs
            }
            lastTriggerMs = nowMs
        }

        fun isIdle(): Boolean =
            !inFlight &&
                attemptCount == 0 &&
                scheduledJob == null &&
                scheduledKind == null &&
                firstTriggerMs == null &&
                lastTriggerMs == null
    }

    private enum class ScheduledKind {
        DEBOUNCE,
        RETRY
    }
}

private const val DEFAULT_SYNC_PAGE_SIZE: Long = 100L
