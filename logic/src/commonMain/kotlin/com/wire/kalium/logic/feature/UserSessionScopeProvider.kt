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
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

internal interface UserSessionScopeProvider {
    suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean = false)
    suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit>
    fun get(userId: UserId): UserSessionScope?
    fun <T : Any> observeValidSession(
        userId: UserId,
        sessionRepository: SessionRepository,
        observe: suspend UserSessionScope.() -> Flow<T>,
    ): Flow<T?>
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
    protected val userAgent: String,
    private val observationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserSessionScopeProvider {

    override suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean) = Unit

    override suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit> = Either.Right(Unit)

    private val sessions = UserSessionRegistry<UserSessionScope>(
        invalidate = { it.invalidateConsumers() },
        cancelFailedStartup = { it.coroutineContext.job.cancel() },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("NestedBlockDepth") // Keep the owned collection and its specific revocation handling together.
    override fun <T : Any> observeValidSession(
        userId: UserId,
        sessionRepository: SessionRepository,
        observe: suspend UserSessionScope.() -> Flow<T>,
    ): Flow<T?> {
        val account = sessions.account(userId)
        val valid = sessionRepository.allSessionsFlow().map { accounts ->
            accounts.any { it.userId == userId && it is AccountInfo.Valid }
        }.distinctUntilChanged()
        // Subscribe to both signals before acquisition; equal metadata still wakes on replacement.
        return channelFlow {
            combine(valid, account.revision) { _, revision -> revision }.collectLatest { revision ->
                send(ObservedValue<T>(null, null))
                val admitted = admitObservation(userId, sessionRepository, revision) ?: return@collectLatest
                try {
                    admitted.work.execute {
                        admitted.value.observe().collect { value ->
                            currentCoroutineContext().ensureActive()
                            if (admitted.isCurrent()) send(ObservedValue(admitted, value))
                        }
                    }
                } catch (cancelled: CancellationException) {
                    // Caller/latest cancellation and genuine callback failures retain their semantics.
                    currentCoroutineContext().ensureActive()
                    if (!cancelled.hasCauseIdentity(admitted.revocation)) {
                        this@channelFlow.cancel(cancelled)
                        throw cancelled
                    }
                    send(ObservedValue<T>(null, null))
                } finally {
                    admitted.work.close()
                }
            }
        }.buffer(0).flowOn(observationDispatcher).transform { result ->
            // Validate after the dispatcher/channel boundary as well: buffered old results cannot
            // authorize resource access. A value already delivered to the caller cannot be retracted.
            if (result.observation?.isCurrent?.invoke() != false) emit(result.value)
        }
    }

    /** Apple supplies its own ownership order; never hold this common mutex around Apple open. */
    @Suppress("ReturnCount") // Do not register observation work until cancellable ownership waiting has returned.
    protected open suspend fun admitObservation(
        userId: UserId,
        sessionRepository: SessionRepository,
        revision: Long,
    ): Observation<UserSessionScope>? {
        val account = sessions.account(userId)
        if (!account.isAvailable(revision)) return null
        val scope = account.withLifecycle {
            validSession(userId, sessionRepository, revision) { getOrCreateObservedSession(userId, revision) }
        } ?: return null
        currentCoroutineContext().ensureActive()
        return admitSessionObservation(userId, revision, scope)
    }

    @Suppress("ReturnCount") // Each unavailable boundary exits before opening or admitting a generation.
    protected suspend fun validSession(
        userId: UserId,
        sessionRepository: SessionRepository,
        revision: Long,
        open: () -> UserSessionScope,
    ): UserSessionScope? {
        val account = sessions.account(userId)
        if (!account.isAvailable(revision)) return null
        // This is deliberately a LIVE read after the cancellable ownership/lifecycle wait.
        val valid = when (val result = sessionRepository.doesValidSessionExist(userId)) {
            is Either.Left -> {
                kaliumLogger.w("Valid-session observation unavailable: ${result.value}")
                false
            }
            is Either.Right -> result.value
        }
        currentCoroutineContext().ensureActive()
        if (!valid || !account.isAvailable(revision)) return null
        val scope = try {
            open()
        } catch (closed: SessionGenerationClosed) {
            currentCoroutineContext().ensureActive()
            return null
        }
        currentCoroutineContext().ensureActive()
        return scope
    }

    protected fun admitSessionObservation(userId: UserId, revision: Long, scope: UserSessionScope): Observation<UserSessionScope>? =
        sessions.account(userId).admit(revision, scope)

    protected suspend fun <R : Any> withClosedSession(
        userId: UserId,
        origin: Any? = null,
        serialize: suspend (suspend () -> R?) -> R?,
        block: suspend (UserSessionScope?) -> R,
    ): R? = sessions.account(userId).withClosed(origin, serialize, block)

    protected fun isSessionAvailable(userId: UserId, revision: Long): Boolean = sessions.account(userId).isAvailable(revision)

    protected fun allowSessionObservation(userId: UserId) = sessions.account(userId).allowObservation()

    /** Apple can release an unused gate without acquiring ownership or evicting shared providers. */
    protected fun releaseUnusedSession(userId: UserId) = sessions.account(userId).release()

    private class ObservedValue<T : Any>(val observation: Observation<UserSessionScope>?, val value: T?)

    override suspend fun open(userId: UserId): UserSessionScope = getOrCreate(userId)

    override suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T = open(userId).action()

    override fun getOrCreate(userId: UserId): UserSessionScope {
        val account = sessions.account(userId)
        val scope = account.getOrCreate(create = { create(userId) }, start = ::startRuntime)
        // Publication and construction have finished; do not wake observers from inside startup.
        account.allowObservation(scope)
        return scope
    }

    protected fun getOrCreateObservedSession(userId: UserId, revision: Long): UserSessionScope =
        sessions.account(userId).getOrCreate(create = { create(userId) }, start = ::startRuntime, observationRevision = revision)

    protected fun hasFailedSessionStartup(userId: UserId): Boolean = sessions.account(userId).hasFailedStartup()

    /** Called only after the registry retains the private owner, before public session admission. */
    internal open fun startRuntime(scope: UserSessionScope) = scope.startRuntime()

    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T = getOrCreate(userId).action()

    override fun get(userId: UserId): UserSessionScope? = sessions.account(userId).get()

    protected fun getRaw(userId: UserId): UserSessionScope? = sessions.account(userId).getRaw()

    protected fun invalidateSession(userId: UserId) = sessions.account(userId).invalidate()

    override suspend fun storeSession(session: StoreSessionParam, sessionRepository: SessionRepository): Either<StorageFailure, Unit> {
        val userId = session.accountTokens.userId
        val account = sessions.account(userId)
        return checkNotNull(account.withClosed { scope ->
            stopAccountWork(scope, account.hasFailedStartup())
            removeResources(userId)
            sessionRepository.storeSession(session).also {
                if (it is Either.Right) account.release(allowObservation = true)
            }
        })
    }

    override suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit> {
        val account = sessions.account(userId)
        return checkNotNull(account.withClosed { scope ->
            stopAccountWork(scope, account.hasFailedStartup())
            sessionRepository.deleteSession(userId).also {
                if (it is Either.Right) {
                    removeResources(userId)
                    account.release()
                }
            }
        })
    }

    override suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean {
        val account = sessions.account(userId)
        return account.withClosed(originatingScope) { scope ->
            prepare()
            stopAccountWork(scope, account.hasFailedStartup())
            clearLocalData()
            removeResources(userId)
            account.release()
            true
        } ?: false
    }

    private suspend fun stopAccountWork(scope: UserSessionScope?, failedStartup: Boolean) {
        try {
            scope?.stopAccountWork()
        } catch (timeout: TimeoutCancellationException) {
            // Only the SDK worker deadline is best effort; caller cancellation retains quarantine.
            currentCoroutineContext().ensureActive()
            // A partially started runtime must complete every shutdown step before resource removal.
            // Retain its owner on timeout, including initialized Cells/calling work still to drain.
            if (failedStartup) throw timeout
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
        account.withClosed { scope ->
            stopAccountWork(scope, account.hasFailedStartup())
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
