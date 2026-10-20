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

package com.wire.kalium.logic.feature

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException

internal interface UserSessionScopeProvider {
    suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean = false)
    suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit>
    fun get(userId: UserId): UserSessionScope?
    suspend fun open(userId: UserId): UserSessionScope
    suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T
    fun getOrCreate(userId: UserId): UserSessionScope
    fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean
    suspend fun delete(userId: UserId)
    suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
    suspend fun storeSession(session: StoreSessionParam, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
}

@Suppress("TooManyFunctions") // Account lifecycle transitions and their shared cleanup belong to one owner.
internal abstract class UserSessionScopeProviderCommon(
    private val globalCallManager: GlobalCallManager,
    private val userStorageProvider: UserStorageProvider,
    private val removeAuthenticatedNetworkForUser: suspend (UserId) -> Unit,
    protected val userAgent: String
) : UserSessionScopeProvider {

    override suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean) = Unit

    override suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit> = Either.Right(Unit)

    private val sessions = UserSessionRegistry<UserSessionScope> { it.invalidateConsumers() }

    override suspend fun open(userId: UserId): UserSessionScope = getOrCreate(userId)

    override suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T = open(userId).action()

    override fun getOrCreate(userId: UserId): UserSessionScope =
        sessions.account(userId).getOrCreate { create(userId) }

    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T = getOrCreate(userId).action()

    override fun get(userId: UserId): UserSessionScope? = sessions.account(userId).get()

    protected fun getRaw(userId: UserId): UserSessionScope? = sessions.account(userId).getRaw()

    protected fun invalidateSession(userId: UserId) = sessions.account(userId).invalidate()

    override suspend fun storeSession(session: StoreSessionParam, sessionRepository: SessionRepository): Either<StorageFailure, Unit> {
        val userId = session.accountTokens.userId
        val account = sessions.account(userId)
        return account.withLifecycle {
            stopAccountWork(account.close())
            removeResources(userId)
            sessionRepository.storeSession(session).also {
                if (it is Either.Right) account.release()
            }
        }
    }

    override suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit> {
        val account = sessions.account(userId)
        return account.withLifecycle {
            stopAccountWork(account.close())
            sessionRepository.deleteSession(userId).also {
                if (it is Either.Right) {
                    removeResources(userId)
                    account.release()
                }
            }
        }
    }

    override suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean {
        val account = sessions.account(userId)
        return account.withLifecycle {
            // Check before prepare: it mutates credentials and account metadata captured by the caller.
            if (!account.isCurrent(originatingScope)) return@withLifecycle false
            val scope = account.close()
            prepare()
            stopAccountWork(scope)
            clearLocalData()
            removeResources(userId)
            account.release()
            true
        }
    }

    private suspend fun stopAccountWork(scope: UserSessionScope?) {
        try {
            scope?.stopAccountWork()
        } catch (timeout: TimeoutCancellationException) {
            // Don't let a stuck account worker block logout.
            kaliumLogger.w(
                "Account work did not stop within ${LogoutUseCaseImpl.SCOPE_DRAIN_TIMEOUT_MS}ms; continuing consumer drain",
                timeout
            )
        }
        // The legacy worker timeout remains best effort. New consumers can also own synchronous
        // construction or caller-context work; never remove their parent resources while it unwinds.
        // Keep this outside the timeout catch so cancellation/failure retains the closing generation.
        scope?.drainConsumers()
    }

    override suspend fun delete(userId: UserId) {
        val account = sessions.account(userId)
        account.withLifecycle {
            stopAccountWork(account.close())
            removeResources(userId)
            account.release()
        }
    }

    private suspend fun removeResources(userId: UserId) {
        globalCallManager.removeInMemoryCallingManagerForUser(userId)
        userStorageProvider.remove(userId)
        removeAuthenticatedNetworkForUser(userId)
    }

    internal abstract fun create(userId: UserId): UserSessionScope
}

internal expect class UserSessionScopeProviderImpl : UserSessionScopeProvider {
    override suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean)
    override suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit>
    override fun get(userId: UserId): UserSessionScope?
    override suspend fun open(userId: UserId): UserSessionScope
    override suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T
    override fun getOrCreate(userId: UserId): UserSessionScope
    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    override suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean
    override suspend fun delete(userId: UserId)
    override suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
    override suspend fun storeSession(session: StoreSessionParam, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
}
