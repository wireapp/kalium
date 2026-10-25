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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
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

    @Suppress("TooGenericExceptionCaught")
    actual override suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean) {
        val account = checkNotNull(accounts[userId]) { "Account preparation requires ownership" }
        account.access.withAccess {
            account.mutex.withLock {
                checkOwnedScope(account, userId, originatingScope)
                try {
                    revokeReadiness(account, userId)
                    account.canRefreshReadiness = false
                    // Restore may replace files behind existing crypto clients. Only a fresh app scope
                    // may validate the restored files and publish readiness again.
                    account.needsCryptoReopen = account.needsCryptoReopen || cryptoReplacement
                } catch (failure: Throwable) {
                    account.unavailable = true
                    throw failure
                }
            }
        }
    }

    actual override suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit> {
        val account = checkNotNull(accounts[userId]) { "Account preparation requires ownership" }
        return account.access.withAccess {
            account.mutex.withLock {
                checkOwnedScope(account, userId, originatingScope)
                account.canRefreshReadiness = false
                prepareOwned(account, userId, checkNotNull(super.get(userId))).also {
                    account.canRefreshReadiness = it is Either.Right
                }
            }
        }
    }

    private fun checkOwnedScope(account: OwnedAccount, userId: UserId, originatingScope: CoroutineScope) {
        check(!account.unavailable && account.lease != null && super.get(userId) === originatingScope) {
            "Account preparation requires the current owned session"
        }
        check(originatingScope.coroutineContext.job.isActive) { "Account workers are stopped" }
    }

    @OptIn(DelicateKaliumApi::class, InternalCryptoAccess::class)
    @Suppress("TooGenericExceptionCaught", "ReturnCount", "LongMethod")
    private suspend fun prepareOwned(
        account: OwnedAccount,
        userId: UserId,
        scope: UserSessionScope,
    ): Either<CoreFailure, Unit> {
        revokeReadiness(account, userId)
        return try {
            check(!account.needsCryptoReopen) { "Reopen the app account after replacing crypto state" }
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
                val prepared = ApplePreparedAccountStore(rootPathsProvider.rootPath, userId).read(checkNotNull(account.lease))
                revokeReadiness(account, userId)
                super.getOrCreate(userId).also { account.canRefreshReadiness = prepared != null }
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

    actual override fun getOrCreate(userId: UserId): UserSessionScope = checkNotNull(get(userId)) {
        "Await CoreLogic.openSessionScope before accessing this Apple account"
    }

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

    /** Keep the session and its public clients alive; transfer only writable/native resources. */
    // Keep ownership on uncertain cleanup, and retry if a concurrent full release replaced the gate.
    @Suppress("ReturnCount")
    suspend fun suspendForBackground(userId: UserId) {
        val account = accounts[userId] ?: return
        while (true) {
            val access = account.access
            var syncSuspension: SyncSuspension? = null
            try {
                access.pause(
                    // Access does not gate the WebSocket: stop sync and close it while sync can still finish
                    // its writes. Pause holds its transition, so reopening cannot resume sync before release.
                    quiesce = {
                        if (access === account.access) syncSuspension = super.get(userId)?.suspendSync()
                    },
                ) {
                    // Drain before taking the lifecycle mutex: admitted operations may still need it
                    // to invalidate notification readiness or complete account preparation.
                    releaseForBackground(account, userId, access)
                }
            } finally {
                // Reopening resumes sync after reacquiring ownership; undo only this suspension if ownership was kept.
                if (account.lease != null && !account.unavailable) syncSuspension?.cancel()
            }
            if (access === account.access) return
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun releaseForBackground(account: OwnedAccount, userId: UserId, access: AccountResourceAccess) {
        account.mutex.withLock {
            if (access !== account.access || account.lease == null) return@withLock
            check(!account.unavailable) { "Account cleanup cannot be established until process exit" }
            try {
                super.get(userId)?.let { scope ->
                    if (account.canRefreshReadiness) prepareOwned(account, userId, scope)
                    else revokeReadiness(account, userId)
                }
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
            super.get(userId)?.let { scope ->
                // Drain before entering crypto: a worker can hold a non-cancellable transaction.
                scope.stopAccountWork()
                // Ordinary release may refresh established readiness, never certify an interrupted login.
                if (account.canRefreshReadiness) prepareOwned(account, userId, scope)
                else revokeReadiness(account, userId)
            }
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
        account.needsCryptoReopen = false
        account.canRefreshReadiness = false
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
                        account.needsCryptoReopen = false
                        account.canRefreshReadiness = false
                        account.crypto.clear()
                        account.access = AccountResourceAccess()
                    }
                }
                result
            } catch (cancellation: CancellationException) {
                throw cancellation
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
                    account.needsCryptoReopen = false
                    account.canRefreshReadiness = false
                    account.crypto.clear()
                    account.access = AccountResourceAccess()
                }
                sessionRepository.storeSession(session).also {
                    // A failed store may already have changed account metadata or credentials.
                    if (it is Either.Right) account.unavailable = false
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
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

    private suspend fun <T> withOwnedAccount(
        account: OwnedAccount,
        userId: UserId,
        operation: suspend () -> T,
    ): T {
        while (true) {
            val access = account.access
            var completed = false
            var result: T? = null
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
                            revokeReadiness(account, userId)
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
            @Suppress("UNCHECKED_CAST")
            if (completed) return result as T
        }
    }

    private suspend fun acquire(account: OwnedAccount, userId: UserId) {
        if (account.lease == null) {
            account.lease = when (val acquired = owner.acquire(userId, OWNERSHIP_TIMEOUT)) {
                is Either.Left -> throw if (acquired.value == AccountRuntimeFailure.BUSY) {
                    AccountBusyException(owner.holder(userId)).also { kaliumLogger.w("Account ownership wait ended: ${it.message}") }
                } else {
                    IllegalStateException("Cannot acquire account ownership: ${acquired.value}")
                }
                is Either.Right -> acquired.value
            }
        }
    }

    private class OwnedAccount {
        val mutex = Mutex()

        @Volatile var access = AccountResourceAccess()

        val crypto = ConcurrentMutableMap<ReopenableCoreCryptoCentral, Unit>()

        @Volatile var lease: AccountRuntimeLease? = null

        @Volatile var unavailable = false

        var needsCryptoReopen = false

        var canRefreshReadiness = false
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
