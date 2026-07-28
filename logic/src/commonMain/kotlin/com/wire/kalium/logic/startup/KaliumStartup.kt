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

package com.wire.kalium.logic.startup

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.persistence.db.DatabaseMigrationObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public interface KaliumStartup {
    public fun session(userId: UserId): StartupHandle<UserSessionScope>
}

public interface StartupHandle<T : Any> {
    public val state: StateFlow<StartupState>

    /**
     * Opens the backing database and returns only after all blocking schema work is complete.
     *
     * Concurrent callers await the same operation.
     */
    public suspend fun open(): StartupResult<T>

    /**
     * Retries a failed startup only when the reported failure is retryable.
     */
    public suspend fun retry(): StartupResult<T>

    /**
     * Returns an already prepared value without opening a database.
     */
    public fun readyOrNull(): T?
}

public sealed interface StartupState {
    public data object NotStarted : StartupState
    public data object Opening : StartupState
    public data class Migrating(public val progress: MigrationProgress) : StartupState
    public data object Ready : StartupState
    public data class Failed(public val failure: StartupFailure) : StartupState
}

public data class MigrationProgress(
    public val stage: Stage,
    public val completedUnits: Long? = null,
    public val totalUnits: Long? = null,
) {
    public enum class Stage {
        Preparing,
        UpdatingSchema,
        MigratingData,
        Finalizing,
    }
}

public data class StartupFailure(
    public val phase: Phase,
    public val isRetryable: Boolean,
) {
    public enum class Phase {
        Opening,
        Migrating,
    }
}

public sealed interface StartupResult<out T : Any> {
    public data class Success<T : Any>(public val value: T) : StartupResult<T>
    public data class Failure(public val failure: StartupFailure) : StartupResult<Nothing>
}

internal class StartupHandleImpl<T : Any>(
    private val startupScope: CoroutineScope,
    private val isRetryableFailure: (Throwable) -> Boolean = { false },
    private val openAction: suspend () -> T,
) : StartupHandle<T> {
    private val stateMutable = MutableStateFlow<StartupState>(StartupState.NotStarted)
    private val readyValue = MutableStateFlow<T?>(null)
    private val mutex = Mutex()
    private var inFlight: Deferred<StartupResult<T>>? = null

    override val state: StateFlow<StartupState> = stateMutable.asStateFlow()

    val migrationObserver: DatabaseMigrationObserver = object : DatabaseMigrationObserver {
        override fun onMigrationStarted(fromVersion: Long, toVersion: Long) {
            stateMutable.value = StartupState.Migrating(
                MigrationProgress(
                    stage = MigrationProgress.Stage.UpdatingSchema,
                )
            )
        }

        override fun onMigrationCompleted(fromVersion: Long, toVersion: Long) {
            stateMutable.value = StartupState.Migrating(
                MigrationProgress(
                    stage = MigrationProgress.Stage.Finalizing,
                )
            )
        }
    }

    override suspend fun open(): StartupResult<T> {
        val ready = readyValue.value
        return if (ready != null) {
            StartupResult.Success(ready)
        } else {
            mutex.withLock {
                inFlight ?: createStartupOperation().also { inFlight = it }
            }.await()
        }
    }

    override suspend fun retry(): StartupResult<T> {
        val failedState = stateMutable.value as? StartupState.Failed
        return when {
            failedState == null -> open()
            !failedState.failure.isRetryable -> StartupResult.Failure(failedState.failure)
            else -> {
                mutex.withLock {
                    if (stateMutable.value == failedState) {
                        inFlight = null
                        stateMutable.value = StartupState.NotStarted
                    }
                }
                open()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createStartupOperation(): Deferred<StartupResult<T>> =
        startupScope.async {
            stateMutable.value = StartupState.Opening
            try {
                openAction().also {
                    readyValue.value = it
                    stateMutable.value = StartupState.Ready
                }.let { StartupResult.Success(it) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val failure = StartupFailure(
                    phase = when (stateMutable.value) {
                        is StartupState.Migrating -> StartupFailure.Phase.Migrating
                        else -> StartupFailure.Phase.Opening
                    },
                    isRetryable = isRetryableFailure(throwable),
                )
                stateMutable.value = StartupState.Failed(failure)
                StartupResult.Failure(failure)
            }
        }

    override fun readyOrNull(): T? = readyValue.value
}
