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

package com.wire.kalium.userstorage.di

import co.touchlab.stately.concurrency.AtomicReference
import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

public data class UserStorage(public val database: UserDatabaseBuilder)

/**
 * Owns the in-process lifecycle and cache for [UserStorage] instances keyed by [UserId].
 *
 * Cache scope is controlled by the shared provider policy [PROVIDER_CACHE_SCOPE]:
 * - [ProviderCacheScope.GLOBAL]: all [UserStorageProvider] instances share the same per-user entries.
 * - [ProviderCacheScope.LOCAL]: each [UserStorageProvider] instance owns private per-user entries.
 */
public abstract class UserStorageProvider {
    private companion object {
        val sharedUserStorageSessions: ConcurrentMutableMap<UserId, UserStorageSession> = ConcurrentMutableMap()
    }

    private val providerUserStorageSessions: ConcurrentMutableMap<UserId, UserStorageSession> =
        when (PROVIDER_CACHE_SCOPE) {
            ProviderCacheScope.GLOBAL -> sharedUserStorageSessions
            ProviderCacheScope.LOCAL -> ConcurrentMutableMap()
        }

    private val preparationScope: CoroutineScope = CoroutineScope(SupervisorJob() + KaliumDispatcherImpl.io)

    public fun get(userId: UserId): UserStorage? =
        (providerUserStorageSessions[userId]?.state?.value as? UserStorageState.Ready)?.storage

    /**
     * Observes the lifecycle of one user's storage without starting storage preparation.
     *
     * The same state flow backs every observer for [userId]. State updates are produced only by
     * calls to [getOrCreate] or [prepare].
     */
    public fun observe(userId: UserId): Flow<UserStorageState> = sessionFor(userId).observableState

    public fun getOrCreate(
        userId: UserId,
        platformUserStorageProperties: PlatformUserStorageProperties,
        shouldEncryptData: Boolean = true,
        dbInvalidationControlEnabled: Boolean,
    ): UserStorage = get(userId) ?: runBlocking {
        when (
            val result = prepare(
                userId,
                platformUserStorageProperties,
                shouldEncryptData,
                dbInvalidationControlEnabled,
            )
        ) {
            is UserStoragePreparationResult.Success -> result.storage
            is UserStoragePreparationResult.Failure -> throw result.exception
        }
    }

    /**
     * Opens and, when needed, migrates the user's storage on the storage I/O scope.
     * Concurrent callers share one preparation attempt.
     */
    public suspend fun prepare(
        userId: UserId,
        platformUserStorageProperties: PlatformUserStorageProperties,
        shouldEncryptData: Boolean = true,
        dbInvalidationControlEnabled: Boolean,
    ): UserStoragePreparationResult {
        val session = sessionFor(userId)
        val preparation = preparationFor(session) {
            create(
                userId,
                shouldEncryptData,
                platformUserStorageProperties,
                dbInvalidationControlEnabled,
                onMigrationStarted = { session.state.value = UserStorageState.MigratingDatabase },
            )
        }
        preparation.start()
        return preparation.await()
    }

    private fun preparationFor(
        session: UserStorageSession,
        createStorage: () -> UserStorage,
    ): Deferred<UserStoragePreparationResult> {
        session.preparation.get()?.let { return it }

        lateinit var candidate: Deferred<UserStoragePreparationResult>
        candidate = preparationScope.async(start = CoroutineStart.LAZY) {
            prepareStorage(session, candidate, createStorage)
        }

        return if (session.preparation.compareAndSet(null, candidate)) {
            candidate
        } else {
            candidate.cancel()
            requireNotNull(session.preparation.get())
        }
    }

    @Suppress("TooGenericExceptionCaught") // Database drivers expose platform-specific exception types.
    private fun prepareStorage(
        session: UserStorageSession,
        preparation: Deferred<UserStoragePreparationResult>,
        createStorage: () -> UserStorage,
    ): UserStoragePreparationResult {
        session.state.value = UserStorageState.OpeningDatabase

        return try {
            val storage = createStorage()
            session.state.value = UserStorageState.Ready(storage)
            UserStoragePreparationResult.Success(storage)
        } catch (exception: CancellationException) {
            session.preparation.compareAndSet(preparation, null)
            session.state.value = UserStorageState.NotStarted
            throw exception
        } catch (exception: Exception) {
            val failure = exception.toUserStoragePreparationFailure()
            session.state.value = UserStorageState.Failed(failure)
            if (failure.canRetry) {
                session.preparation.compareAndSet(preparation, null)
            }
            UserStoragePreparationResult.Failure(failure, exception)
        }
    }

    private fun sessionFor(userId: UserId): UserStorageSession =
        providerUserStorageSessions.computeIfAbsent(userId) {
            UserStorageSession()
        }

    private class UserStorageSession {
        val state: MutableStateFlow<UserStorageState> = MutableStateFlow(UserStorageState.NotStarted)
        val observableState: Flow<UserStorageState> = state.asStateFlow()
        val preparation: AtomicReference<Deferred<UserStoragePreparationResult>?> = AtomicReference(null)
    }

    protected abstract fun create(
        userId: UserId,
        shouldEncryptData: Boolean,
        platformProperties: PlatformUserStorageProperties,
        dbInvalidationControlEnabled: Boolean
    ): UserStorage

    @Suppress("LongParameterList")
    protected open fun create(
        userId: UserId,
        shouldEncryptData: Boolean,
        platformProperties: PlatformUserStorageProperties,
        dbInvalidationControlEnabled: Boolean,
        onMigrationStarted: () -> Unit,
    ): UserStorage = create(userId, shouldEncryptData, platformProperties, dbInvalidationControlEnabled)

    public fun remove(userId: UserId): UserStorage? {
        val session = providerUserStorageSessions.remove(userId) ?: return null
        session.preparation.get()?.cancel()
        return (session.state.value as? UserStorageState.Ready)?.storage
    }
}

public expect class PlatformUserStorageProperties
