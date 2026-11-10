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
@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature

import com.wire.kalium.util.AccountResourceAccess
import com.wire.kalium.cryptography.ReopenableCoreCryptoCentral
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.persistence.db.refreshAfterExternalWrites
import com.wire.kalium.share.deleteShareOutbox

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapProteusRequest
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.receiving.PreparedAccount
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.persistence.dbPassphrase.existingCoreCryptoKey
import com.wire.kalium.persistence.dbPassphrase.MLS_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.dbPassphrase.PROTEUS_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalCryptoAccess
import okio.FileSystem
import okio.Path.Companion.toPath
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.sync.receiving.ApplePreparedAccountStore
import com.wire.kalium.logic.sync.SyncSuspension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import com.wire.kalium.logic.sync.receiving.AccountRuntimeFailure
import com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntimeOwner
import com.wire.kalium.logic.sync.receiving.appleAccountFileKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DBFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncShutdownCoordinator
import com.wire.kalium.logic.util.databaseKey
import com.wire.kalium.logic.util.databaseSecurityHelper
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorageProvider

@Suppress("LongParameterList", "TooManyFunctions")
internal actual open class UserSessionScopeProviderImpl(
    private val authenticationScopeProvider: AuthenticationScopeProvider,
    private val rootPathsProvider: RootPathsProvider,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs,
    private val globalPreferences: GlobalPrefProvider,
    private val globalCallManager: GlobalCallManager,
    private val globalDatabaseBuilder: GlobalDatabaseBuilder,
    private val userStorageProvider: UserStorageProvider,
    private val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider,
    private val networkStateObserver: NetworkStateObserver,
    private val logoutCallback: LogoutCallback,
    userAgent: String,
    private val keychainConfig: ApplePersistenceConfig,
    private val syncShutdownCoordinator: SyncShutdownCoordinator,
) : UserSessionScopeProviderCommon(
        globalCallManager,
        userStorageProvider,
        { userId -> userAuthenticatedNetworkProvider.remove(userId.toApi()) },
        userAgent,
),
    UserSessionScopeProvider {
    private val securityHelper = databaseSecurityHelper(globalPreferences.passphraseStorage)
    private val owner = AppleAccountRuntimeOwner(rootPathsProvider.rootPath)
    private val accounts = ConcurrentMutableMap<UserId, OwnedAccount>()

    /**
     * iOS kills a suspended app that keeps an account's lock. The app takes a lock only while it runs in the foreground,
     * or while a [hold] gives that account execution time of its own; everything else waits.
     */
    private val execution = MutableStateFlow(Execution())

    /** Accounts whose release threw with their lock held: only process exit lets those locks go. */
    private val failedReleases = MutableStateFlow(emptySet<UserId>())

    private data class Execution(val foreground: Boolean = true, val holds: Map<UserId, List<Hold>> = emptyMap()) {
        fun allows(userId: UserId) = foreground || userId in holds

        fun with(userId: UserId, hold: Hold) = copy(holds = holds + (userId to holds[userId].orEmpty() + hold))

        fun without(userId: UserId, hold: Hold): Execution {
            val rest = holds[userId].orEmpty() - hold
            return copy(holds = if (rest.isEmpty()) holds - userId else holds + (userId to rest))
        }
    }

    /** One holder's claim, compared by identity: why it keeps the account and since when. */
    private class Hold(val reason: String) {
        val since = TimeSource.Monotonic.markNow()
    }

    /** Execution ended while an operation was about to take the lock: it waits for execution and starts again. */
    private class NoExecution : Exception("The app has no execution time for this account")

    /** Readiness is durable: revoke it before a change the extension cannot detect, such as registration. */
    actual override suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope) {
        val account = checkNotNull(accounts[userId]) { "Account preparation requires ownership" }
        account.access.withAccess {
            account.mutex.withLock {
                checkOwnedScope(account, userId, originatingScope)
                revokeReadiness(account, userId)
            }
        }
    }

    actual override suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit> {
        val account = checkNotNull(accounts[userId]) { "Account preparation requires ownership" }
        return account.access.withAccess {
            account.mutex.withLock {
                checkOwnedScope(account, userId, originatingScope)
                prepareOwned(account, userId, checkNotNull(super.get(userId)))
            }
        }
    }

    private fun checkOwnedScope(account: OwnedAccount, userId: UserId, originatingScope: CoroutineScope) {
        check(!account.unavailable && account.lease != null && super.get(userId) === originatingScope) {
            "Account preparation requires the current owned session"
        }
        check(originatingScope.coroutineContext.job.isActive) { "Account workers are stopped" }
    }

    /**
     * Keeps the last readiness while verifying: the extension refuses state that changed since, and an app killed
     * mid-check must not leave it blind. A failed check revokes; cancellation leaves it as it was.
     */
    private suspend fun prepareOwned(
        account: OwnedAccount,
        userId: UserId,
        scope: UserSessionScope,
    ): Either<CoreFailure, Unit> = verifyAndPublish(account, userId, scope).also {
        if (it is Either.Left) revokeReadiness(account, userId)
    }

    @OptIn(DelicateKaliumApi::class, InternalCryptoAccess::class)
    @Suppress("TooGenericExceptionCaught", "ReturnCount", "LongMethod")
    private suspend fun verifyAndPublish(
        account: OwnedAccount,
        userId: UserId,
        scope: UserSessionScope,
    ): Either<CoreFailure, Unit> {
        return try {
            val session = globalScope.sessionRepository.fullAccountInfo(userId).getOrElse { return Either.Left(it) }
            check(globalScope.sessionRepository.doesValidSessionExist(userId).getOrElse { return Either.Left(it) })
            val token = checkNotNull(globalPreferences.authTokenStorage.getToken(userId.toDao())) {
                "Prepared account token is missing"
            }
            check(token.userId == userId.toDao() && token.accessToken.isNotBlank() && token.refreshToken.isNotBlank())
            val clientId = scope.clientRepository.currentClientId().getOrElse { return Either.Left(it) }
            val blockedByE2EI = scope.clientRepository.isClientRegistrationBlockedByE2EI().getOrElse { return Either.Left(it) }
            check(!blockedByE2EI)
            val hasMLS = scope.clientRepository.hasRegisteredMLSClient().getOrElse { return Either.Left(it) }
            val keys = globalPreferences.passphraseStorage
            checkNotNull(keys.existingCoreCryptoKey("${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$userId")) {
                "Prepared Proteus key is missing"
            }
            requireCryptoFile("${rootPathsProvider.rootProteusPath(userId)}/keystore")
            val proteus = scope.proteusClientProvider.getOrError().getOrElse {
                return Either.Left(it)
            }
            wrapProteusRequest {
                proteus.transaction("prepare-receiving") { it.getLocalFingerprint() }
            }.getOrElse { return Either.Left(it) }
            val cipherSuite = if (hasMLS) {
                checkNotNull(keys.existingCoreCryptoKey("${MLS_DB_PASSPHRASE_PREFIX_V2}_$userId")) { "Prepared MLS key is missing" }
                requireCryptoFile("${rootPathsProvider.rootMLSPath(userId)}/${clientId.value}/keystore/keystore")
                val configured = scope.userConfigRepository.getSupportedCipherSuite().getOrElse { return Either.Left(it) }.default
                check(configured !is CipherSuite.UNKNOWN) { "Unsupported prepared MLS cipher suite" }
                val mls = scope.mlsClientProvider.getMLSClient(clientId).getOrElse { return Either.Left(it) }
                check(mls.getDefaultCipherSuite() == configured.toCrypto()) { "Prepared MLS cipher suite changed" }
                check(mls.getCredentialRef(CredentialType.Basic) != null || mls.getCredentialRef(CredentialType.X509) != null) {
                    "Prepared MLS credential is missing"
                }
                configured
            } else null
            val lease = checkNotNull(account.lease)
            ApplePreparedAccountStore(rootPathsProvider.rootPath, userId).publish(
                lease,
                PreparedAccount(userId, clientId.value, session.serverConfig, cipherSuite)
            )
            Either.Right(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            Either.Left(CoreFailure.Unknown(failure))
        }
    }

    private fun requireCryptoFile(path: String) {
        val metadata = FileSystem.SYSTEM.metadataOrNull(path.toPath())
        check(metadata?.isRegularFile == true && (metadata.size ?: 0) > 0) { "Prepared crypto store is missing" }
    }

    /** In the foreground accounts open on first use; in the background only held accounts may take their lock. */
    fun setForeground(foreground: Boolean) {
        execution.update { it.copy(foreground = foreground) }
    }

    /**
     * Releases every account no hold keeps, all at once: one slow drain must not keep the other accounts' locks.
     * Call after [setForeground] false. Returns whether every release succeeded.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun releaseBackgroundAccounts(): Boolean = coroutineScope {
        val released = accounts.keys.toList().map { userId ->
            async {
                try {
                    releaseUnlessAllowed(userId)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    kaliumLogger.e("Account release for the background failed: ${failure::class.simpleName}")
                    false
                }
            }
        }.awaitAll().all { it }
        kaliumLogger.i(lockedAccounts().toString())
        released
    }

    /** The accounts this process still holds the lock of, with what keeps each: hold reasons, or nothing. */
    fun lockedAccounts(): AccountLocks {
        val current = execution.value
        val failed = failedReleases.value
        val owned = accounts.keys.toList().filter { accounts[it]?.lease != null }
        val locked = owned.map { userId ->
            val reasons = current.holds[userId].orEmpty().map { "${it.reason}@${it.since.elapsedNow().inWholeSeconds}s" } +
                listOfNotNull(ACCOUNT_LOCK_UNAVAILABLE.takeIf { userId in failed })
            // Pseudonymous: a prefix of the lock file's identity hash.
            appleAccountFileKey(userId).take(ACCOUNT_KEY_LENGTH) to reasons.ifEmpty {
                listOf(if (current.foreground) "FOREGROUND" else "RELEASING")
            }
        }
        // In the foreground the app still runs every account it holds.
        val hasUnreleasable = !current.foreground && owned.any { it in failed }
        return AccountLocks(locked, hasHolds = current.holds.isNotEmpty(), hasUnreleasable = hasUnreleasable)
    }

    /**
     * Opens [userId] for work with execution time of its own, such as a call; it keeps the account until released.
     * [reason] names that work in the background log, such as CALL or UPLOAD.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun hold(userId: UserId, reason: String): AccountExecutionHold {
        // Count the hold before opening: a background release that decides after this keeps the account.
        val hold = Hold(reason)
        execution.update { it.with(userId, hold) }
        val scope = try {
            open(userId)
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                execution.update { it.without(userId, hold) }
                try {
                    releaseUnlessAllowed(userId)
                } catch (cleanup: Throwable) {
                    failure.addSuppressed(cleanup)
                }
            }
            throw failure
        }
        return AccountExecutionHold(scope, endHold = { execution.update { it.without(userId, hold) } }) {
            releaseUnlessAllowed(userId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    actual override suspend fun open(userId: UserId): UserSessionScope {
        val account = accounts.computeIfAbsent(userId) { OwnedAccount() }
        return withOwnedAccount(account, userId) {
            check(!account.unavailable) { "Account resources are unavailable until process exit" }
            super.get(userId)?.let {
                if (!it.coroutineContext.job.isActive) {
                    account.unavailable = true
                    error("Account workers stopped before resources were released")
                }
                return@withOwnedAccount it
            }
            acquire(account, userId)
            try {
                // Opening keeps published readiness: if this process dies, the extensions keep receiving.
                super.getOrCreate(userId)
            } catch (failure: Throwable) {
                // A constructor can fail after opening native resources that never reached a provider.
                account.unavailable = true
                throw failure
            }
        }
    }

    actual override fun get(userId: UserId): UserSessionScope? =
        accounts[userId]?.takeIf { !it.unavailable }?.let {
            super.get(userId)?.takeIf { scope -> scope.coroutineContext.job.isActive }
        }

    actual override fun getOrCreate(userId: UserId): UserSessionScope =
        get(userId) ?: throw AccountSessionUnavailableException()

    actual override suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean {
        val account = accounts[userId]?.takeIf { super.get(userId) === originatingScope } ?: return false
        return withOwnedAccount(account, userId) {
            // A queued logout for an old scope must not mutate a released or reopened account.
            if (super.get(userId) !== originatingScope) return@withOwnedAccount false
            check(!account.unavailable) { "Account cleanup cannot be established until process exit" }
            acquire(account, userId)
            account.unavailable = true
            revokeReadiness(account, userId)
            prepare()
            super.get(userId)?.stopAccountWork()
            clearLocalData()
            // Shares handed to the app live outside the account's directory; they leave with the account.
            deleteShareOutbox(rootPathsProvider.rootPath, userId)
            withContext(NonCancellable) { closeAndRelease(account, userId) }
            true
        }
    }

    /**
     * Releases [userId] if the app may not run it now: no foreground and no hold. Keeps the session and its public
     * clients alive; transfers only writable/native resources.
     */
    // Keep ownership on uncertain cleanup, and retry if a concurrent full release replaced the gate.
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private suspend fun releaseUnlessAllowed(userId: UserId) {
        val account = accounts[userId] ?: return
        // The pause decides again under the gate's transition; this only skips it while the app may run the account.
        if (execution.value.allows(userId)) return
        while (true) {
            val access = account.access
            var syncSuspension: SyncSuspension? = null
            try {
                pauseForBackground(account, userId, access) { syncSuspension = it }
            } catch (failure: Exception) {
                // A release that threw with the lock still held cannot be retried; a cancelled one can.
                if (failure !is CancellationException && account.lease != null) failedReleases.update { it + userId }
                throw failure
            } finally {
                // Reopening resumes sync after reacquiring ownership; undo only this suspension if ownership was kept.
                if (account.lease != null && !account.unavailable) syncSuspension?.cancel()
            }
            if (access === account.access) return
        }
    }

    /**
     * A message send keeps its crypto transaction while its request waits on a bad network, up to the request
     * timeout, which is longer than iOS keeps the app running; stopping sync can wait behind it too. After
     * [STALLED_REQUEST_GRACE], fail the account's requests as offline until closing starts, so the account is still
     * released.
     */
    private suspend fun pauseForBackground(
        account: OwnedAccount,
        userId: UserId,
        access: AccountResourceAccess,
        onSyncSuspended: (SyncSuspension?) -> Unit,
    ) = coroutineScope {
        val failingStalledRequests = launch {
            delay(STALLED_REQUEST_GRACE)
            super.get(userId)?.let { scope ->
                kaliumLogger.w("Account release is waiting on requests; failing them as offline")
                scope.failNetworkRequests { awaitCancellation() }
            }
        }
        try {
            access.pause(
                // Decided under the gate's transition: a hold or the foreground that came first keeps the account.
                shouldPause = { !execution.value.allows(userId) },
                // Access does not gate the WebSocket: stop sync and close it while sync can still finish
                // its writes. Pause holds its transition, so reopening cannot resume sync before release.
                quiesce = {
                    if (access === account.access) onSyncSuspended(super.get(userId)?.suspendSync())
                },
            ) {
                failingStalledRequests.cancelAndJoin()
                // Drain before taking the lifecycle mutex: admitted operations may still need it
                // to invalidate notification readiness or complete account preparation.
                releaseForBackground(account, access)
            }
        } finally {
            failingStalledRequests.cancel()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun releaseForBackground(account: OwnedAccount, access: AccountResourceAccess) {
        account.mutex.withLock {
            if (access !== account.access || account.lease == null) return@withLock
            check(!account.unavailable) { "Account cleanup cannot be established until process exit" }
            try {
                // Readiness is left as published: no checks or crypto work in the short background window.
                account.crypto.keys.forEach { it.suspendNativeResources() }
                account.lease?.release()
                account.lease = null
            } catch (failure: Throwable) {
                account.unavailable = true
                throw failure
            }
        }
    }

    /** The host must quiesce account use cases and global account collectors before requesting release. */
    actual override suspend fun delete(userId: UserId) = withContext(NonCancellable) {
        val account = accounts[userId] ?: return@withContext
        withOwnedAccount(account, userId) {
            check(!account.unavailable) { "Account cleanup cannot be established until process exit" }
            acquire(account, userId)
            account.unavailable = true
            // Drain before closing crypto: a worker can hold a non-cancellable transaction.
            super.get(userId)?.stopAccountWork()
            closeAndRelease(account, userId)
        }
    }

    private suspend fun closeAndRelease(account: OwnedAccount, userId: UserId) {
        // On any failure keep the scope and lease: successful retry cannot establish native cleanup.
        super.get(userId)?.closeAccountResources()
        super.delete(userId)
        account.lease?.release()
        account.lease = null
        account.unavailable = false
        account.crypto.clear()
        account.access = AccountResourceAccess()
    }

    @Suppress("TooGenericExceptionCaught")
    actual override suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit> {
        val account = accounts.computeIfAbsent(userId) { OwnedAccount() }
        return withOwnedAccount(account, userId) {
            try {
                check(!account.unavailable) { "Account cleanup cannot be established until process exit" }
                acquire(account, userId)
                account.unavailable = true
                revokeReadiness(account, userId)
                withContext(NonCancellable) { super.get(userId)?.closeAccountResources() }
                val result = sessionRepository.deleteSession(userId)
                if (result is Either.Right) {
                    withContext(NonCancellable) {
                        super.delete(userId)
                        account.lease?.release()
                        account.lease = null
                        account.unavailable = false
                        account.crypto.clear()
                        account.access = AccountResourceAccess()
                    }
                }
                result
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (noExecution: NoExecution) {
                // Thrown before anything changed: wait for execution instead of failing.
                throw noExecution
            } catch (failure: Throwable) {
                Either.Left(StorageFailure.Generic(failure))
            }
        }
    }

    /** The host must quiesce public operations and collectors before replacing a cached session. */
    @Suppress("TooGenericExceptionCaught")
    actual override suspend fun storeSession(
        session: StoreSessionParam,
        sessionRepository: SessionRepository,
    ): Either<StorageFailure, Unit> {
        val userId = session.accountTokens.userId
        val account = accounts.computeIfAbsent(userId) { OwnedAccount() }
        return withOwnedAccount(account, userId) {
            try {
                check(!account.unavailable) { "Account cleanup cannot be established until process exit" }
                acquire(account, userId)
                account.unavailable = true
                revokeReadiness(account, userId)
                // Drain old token refreshes before replacing credentials, retaining ownership throughout.
                withContext(NonCancellable) {
                    super.get(userId)?.closeAccountResources()
                    super.delete(userId)
                    account.crypto.clear()
                    account.access = AccountResourceAccess()
                }
                sessionRepository.storeSession(session).also {
                    // A failed store may already have changed account metadata or credentials.
                    if (it is Either.Right) account.unavailable = false
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (noExecution: NoExecution) {
                // Thrown before anything changed: a login that finishes in the background waits for the foreground.
                throw noExecution
            } catch (failure: Throwable) {
                Either.Left(StorageFailure.Generic(failure))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun revokeReadiness(account: OwnedAccount, userId: UserId) {
        try {
            ApplePreparedAccountStore(rootPathsProvider.rootPath, userId).revoke(checkNotNull(account.lease))
        } catch (failure: Throwable) {
            account.unavailable = true
            throw failure
        }
    }

    /**
     * Runs [operation] owning the account, reopening it first if the background release paused it. Without execution
     * time this waits here, outside the account's gate, and reads the gate again afterwards: a gate that a logout or a
     * login replaced meanwhile cannot strand it. Work the gate already admitted finishes without waiting.
     */
    private suspend fun <T> withOwnedAccount(
        account: OwnedAccount,
        userId: UserId,
        operation: suspend () -> T,
    ): T {
        while (true) {
            val admitted = account.access.admitsCaller()
            if (!admitted) execution.first { it.allows(userId) }
            val access = account.access
            var completed = false
            var result: T? = null
            try {
                access.withResumedAccess(
                    // Another process may hold the lease for long: wait here, where cancellation still works and a
                    // failure keeps the account paused instead of unavailable.
                    acquireOwnership = {
                        account.mutex.withLock {
                            if (access === account.access && !account.unavailable) acquire(account, userId)
                        }
                    },
                    openResources = {
                        account.mutex.withLock {
                            check(!account.unavailable) { "Account resources are unavailable until process exit" }
                            if (access === account.access) {
                                acquire(account, userId)
                                super.get(userId)?.let { session ->
                                    session.userStorage.database.refreshAfterExternalWrites()
                                    session.resumeSync()
                                }
                            }
                        }
                    },
                    operation = {
                        account.mutex.withLock {
                            if (access === account.access) {
                                result = operation()
                                completed = true
                            }
                        }
                    },
                )
            } catch (noExecution: NoExecution) {
                // Admitted work would hold up the drain it waits on, and a retry here would rerun its caller: fail it.
                if (admitted) throw IllegalStateException("Account work admitted without the lock cannot wait for execution", noExecution)
                // Nothing changed: wait for execution time, then start again on the current gate.
                continue
            }
            @Suppress("UNCHECKED_CAST")
            if (completed) return result as T
        }
    }

    /**
     * Takes the account's lock only while the app may run: a wait for another process ends when execution does, and a
     * lock won just as execution ended is let go again. Operations call this before changing anything.
     */
    private suspend fun acquire(account: OwnedAccount, userId: UserId) {
        if (account.lease != null) return
        requireExecution(userId)
        var revoked = false
        val acquired = owner.acquire(userId, OWNERSHIP_TIMEOUT) { execution.value.allows(userId).also { revoked = !it } }
        // One decision: a wait execution ended is no busy owner, and a lock won as it ended is let go.
        if (revoked || !execution.value.allows(userId)) {
            (acquired as? Either.Right)?.value?.release()
            throw NoExecution()
        }
        account.lease = when (acquired) {
            is Either.Left -> throw if (acquired.value == AccountRuntimeFailure.BUSY) {
                AccountBusyException(owner.holder(userId)).also { kaliumLogger.w("Account ownership wait ended: ${it.message}") }
            } else {
                IllegalStateException("Cannot acquire account ownership: ${acquired.value}")
            }
            is Either.Right -> acquired.value
        }
    }

    private fun requireExecution(userId: UserId) {
        if (!execution.value.allows(userId)) throw NoExecution()
    }

    private class OwnedAccount {
        val mutex = Mutex()

        @Volatile var access = AccountResourceAccess()

        val crypto = ConcurrentMutableMap<ReopenableCoreCryptoCentral, Unit>()

        @Volatile var lease: AccountRuntimeLease? = null

        @Volatile var unavailable = false
    }

    override fun create(userId: UserId): UserSessionScope {
        val account = checkNotNull(accounts[userId])
        check(account.lease != null) { "Account construction requires ownership" }
        val access = account.access
        val rootAccountPath = rootPathsProvider.rootAccountPath(userId)
        val rootStoragePath = "$rootAccountPath/storage"
        val rootFileSystemPath = AssetsStorageFolder("$rootStoragePath/files")
        val rootCachePath = CacheFolder("$rootAccountPath/cache")
        val dbPath = DBFolder("$rootAccountPath/database")
        val dataStoragePaths = DataStoragePaths(rootFileSystemPath, rootCachePath, dbPath)
        return UserSessionScope(
            PlatformUserStorageProperties(
                rootPath = rootPathsProvider.rootPath,
                keychainConfig = keychainConfig,
                rootStoragePath = rootStoragePath,
                accountResourceAccess = access,
                userDbSecretProvider = { userId ->
                    val databaseName = FileNameUtil.userDBName(UserIDEntity(userId.value, userId.domain))
                    databaseKey(
                        databasePath = "$rootStoragePath/$databaseName",
                        existingKey = { securityHelper.userDBOrSecretNull(userId) },
                        newKey = { securityHelper.userDBSecret(userId, databaseExists = false) }
                    )
                },
            ),
            userId,
            globalScope,
            globalCallManager,
            globalPreferences,
            globalDatabaseBuilder,
            authenticationScopeProvider,
            rootPathsProvider,
            dataStoragePaths,
            kaliumConfigs,
            userStorageProvider,
            userAuthenticatedNetworkProvider,
            this,
            networkStateObserver,
            logoutCallback,
            userAgent,
            syncShutdownCoordinator,
            coreCryptoCentralFactory = { root, key ->
                ReopenableCoreCryptoCentral(access) { coreCryptoCentral(root, key) }.also {
                    account.crypto[it] = Unit
                }
            },
        )
    }

}

/**
 * An NSE attempt holds an account for a few seconds, within its 20 s budget. Waiting longer only keeps app lifecycle
 * work behind an extension that iOS suspended with its host; callers fail or retry instead.
 */
private val OWNERSHIP_TIMEOUT = 15.seconds

/** Short enough to keep account keys pseudonymous in logs, long enough to tell accounts apart. */
private const val ACCOUNT_KEY_LENGTH = 8

/**
 * Long enough for a send on a working network to finish, short enough that the release still ends while iOS keeps
 * the app running: a 0xdead10cc report showed iOS suspending the app about 4.8 s after it entered the background.
 */
private val STALLED_REQUEST_GRACE = 1.seconds
