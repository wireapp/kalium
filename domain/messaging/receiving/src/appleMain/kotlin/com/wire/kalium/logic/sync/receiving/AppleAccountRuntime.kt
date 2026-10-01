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

@file:OptIn(
    com.wire.kalium.util.InternalKaliumApi::class,
    kotlinx.coroutines.DelicateCoroutinesApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.e2ei.PkiHttpRequests
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.ExistingDatabaseException
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.existingUserDatabaseBuilder
import com.wire.kalium.persistence.dbPassphrase.MLS_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.dbPassphrase.PROTEUS_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.dbPassphrase.existingCoreCryptoKey
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.usernetwork.ExistingAccountSessionManager
import com.wire.kalium.util.InternalKaliumApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Concrete extension resources for one owned, login-prepared account. Resources may only be used
 * inside [use]. This opens no global SQLite or app session and never generates keys or repairs state.
 * CoreCrypto still uses its available open-or-create API after practical prepared-file checks.
 */
@InternalKaliumApi
public class AppleAccountRuntime private constructor(
    public val prepared: PreparedAccount,
    private val lease: AccountRuntimeLease,
    internal val assetDirectory: String,
) {
    public val processingScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    public val database: UserDatabaseBuilder get() = checkNotNull(openedDatabase)
    public val network: AuthenticatedNetworkContainer get() = checkNotNull(openedNetwork)
    public val proteusCoreCrypto: CoreCryptoCentral get() = checkNotNull(openedProteus)
    public val mlsCoreCrypto: CoreCryptoCentral? get() = openedMLS

    /** Canonical default for this attempt; changing it requires an already installed credential. */
    public var mlsCipherSuite: CipherSuite? = prepared.mlsCipherSuite
        private set

    internal lateinit var sessionManager: ExistingAccountSessionManager
        private set

    internal lateinit var invalidateReceiving: () -> Unit
        private set

    private var openedDatabase: UserDatabaseBuilder? = null
    private var sqlDispatcher: CloseableCoroutineDispatcher? = null
    private var openedNetwork: AuthenticatedNetworkContainer? = null
    private var pkiHttpClient: HttpClient? = null
    private var openedProteus: CoreCryptoCentral? = null
    private var openedMLS: CoreCryptoCentral? = null
    private var cleanupUncertain = false

    @Suppress("LongMethod", "TooGenericExceptionCaught")
    private suspend fun open(rootPath: String, keychainConfig: ApplePersistenceConfig, userAgent: String, pins: CertificatePinning) {
        val userId = prepared.userId
        invalidateReceiving = { ApplePreparedAccountStore(rootPath, userId).invalidate(lease, prepared.clientId) }
        val root = appleAccountPath(rootPath, userId)
        val preferences = GlobalPrefProvider(keychainConfig)
        val session = ExistingAccountSessionManager(
            userId,
            ClientId(prepared.clientId),
            prepared.serverConfig,
            prepared.nomadServiceUrl,
            preferences.authTokenStorage
        ).also { sessionManager = it }
        // Darwin currently rejects either proxy configuration or proxy credentials.
        check(prepared.serverConfig.links.apiProxy == null && session.proxyCredentials() == null) {
            "Apple proxy is unsupported"
        }
        val proteusKey = checkNotNull(preferences.passphraseStorage.existingCoreCryptoKey("${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$userId"))
        val proteusPath = "$root/proteus"
        requireCryptoFile("$proteusPath/keystore")
        val mlsPath = "$root/mls/${prepared.clientId}/keystore"
        val mlsKey = prepared.mlsCipherSuite?.let {
            check(it !is CipherSuite.UNKNOWN) { "Prepared MLS cipher suite is unsupported" }
            requireCryptoFile("$mlsPath/keystore")
            checkNotNull(preferences.passphraseStorage.existingCoreCryptoKey("${MLS_DB_PASSPHRASE_PREFIX_V2}_$userId"))
        }
        try {
            // The pinned native SQLite driver binds a transaction to its thread. A limited
            // parallelism view alone can resume on another thread after network suspension.
            val dispatcher = newSingleThreadContext("nse-sql").also { sqlDispatcher = it }
            openedDatabase = existingUserDatabaseBuilder("$root/storage", UserIDEntity(userId.value, userId.domain), dispatcher)
        } catch (failure: ExistingDatabaseException) {
            cleanupUncertain = failure.cleanupUncertain
            throw failure
        }
        val registration = ClientRegistrationStorageImpl(database.metadataDAO)
        check(registration.getRegisteredClientId() == prepared.clientId && !registration.isBlockedByE2EI()) {
            "Prepared client registration changed"
        }
        check(registration.hasRegisteredMLSClient() == (mlsKey != null)) { "Prepared MLS registration changed" }
        if (mlsKey != null) {
            mlsCipherSuite = CipherSuite.fromTag(checkNotNull(database.userConfigDAO.getDefaultCipherSuite()).default)
            check(mlsCipherSuite !is CipherSuite.UNKNOWN) { "Configured MLS cipher suite is unsupported" }
        }
        // A failing native factory can allocate a handle before returning it. Clear this flag only
        // after each returned handle has been retained; known cleanup cannot recover a lost handle.
        cleanupUncertain = true
        openedProteus = coreCryptoCentral(proteusPath, proteusKey)
        cleanupUncertain = false
        if (mlsKey != null) {
            cleanupUncertain = true
            openedMLS = coreCryptoCentral(mlsPath, mlsKey)
            cleanupUncertain = false
            if (mlsCipherSuite != prepared.mlsCipherSuite) {
                val available = checkNotNull(openedMLS).hasCredential(
                    CryptoQualifiedClientId(prepared.clientId, userId.toCrypto()),
                    checkNotNull(mlsCipherSuite).toCrypto()
                )
                check(available) { "Configured MLS cipher requires an installed credential" }
            }
        }
        cleanupUncertain = true
        openedNetwork = AuthenticatedNetworkContainer.create(
            session, prepared.nomadServiceUrl, QualifiedID(userId.value, userId.domain), userAgent,
            pins, null, null, kaliumLogger
        )
        cleanupUncertain = false
        openedMLS?.let { central ->
            cleanupUncertain = true
            val unbound = UnboundNetworkContainerCommon(userAgent, false, pins, null, false)
            val http = unbound.cellsClient.also { pkiHttpClient = it }
            cleanupUncertain = false
            val config = UserConfigDataSource(
                database.userPrefsDAO,
                database.userConfigDAO,
                lazy { BuildFileRestrictionState.NoRestriction }
            )
            central.configurePkiEnvironment(ReceivingPkiHooks(PkiHttpRequests(config, unbound.acmeApi, http), processingScope))
        }
        currentCoroutineContext().ensureActive()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun close() = withContext(NonCancellable) {
        try {
            // If resource users cannot be drained, leave every handle and the lease retained.
            withTimeout(DRAIN_TIMEOUT_MS) { processingScope.coroutineContext.job.cancelAndJoin() }
            var closeFailure: Throwable? = null
            val resources: List<suspend () -> Unit> = listOf(
                { openedNetwork?.close() },
                { openedMLS?.close() },
                {
                    pkiHttpClient?.let { http ->
                        http.close()
                        http.coroutineContext.job.join()
                        http.engine.close()
                        http.engine.coroutineContext.job.join()
                    }
                },
                { openedProteus?.close() },
                { openedDatabase?.close() },
                { sqlDispatcher?.close() }
            )
            for (closeResource in resources) {
                try {
                    closeResource()
                } catch (failure: Throwable) {
                    if (closeFailure == null) closeFailure = failure else closeFailure.addSuppressed(failure)
                }
            }
            closeFailure?.let { throw it }
            check(!cleanupUncertain) { "Native account opening could not establish cleanup" }
            lease.release()
        } catch (failure: Throwable) {
            // Strongly retain known resources and the descriptor. Never retry uncertain native close.
            quarantined.update { it + this@AppleAccountRuntime }
            throw failure
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> run(
        rootPath: String,
        keychainConfig: ApplePersistenceConfig,
        userAgent: String,
        certificatePinning: CertificatePinning,
        block: suspend (AppleAccountRuntime) -> T,
    ): T {
        var operationFailure: Throwable? = null
        return try {
            open(rootPath, keychainConfig, userAgent, certificatePinning)
            processingScope.async { block(this@AppleAccountRuntime) }.await()
        } catch (failure: Throwable) {
            operationFailure = failure
            throw failure
        } finally {
            closePreservingFailure(operationFailure)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun closePreservingFailure(operationFailure: Throwable?) {
        try {
            close()
        } catch (failure: Throwable) {
            if (operationFailure == null) throw failure
            operationFailure.addSuppressed(failure)
        }
    }

    public companion object {
        private const val DRAIN_TIMEOUT_MS = 3_000L
        private val quarantined = MutableStateFlow<List<AppleAccountRuntime>>(emptyList())

        /** Open, run owned work, drain and close before releasing ownership, including on cancellation. */
        @Suppress("LongParameterList", "TooGenericExceptionCaught", "ReturnCount")
        public suspend fun <T> use(
            rootPath: String,
            userId: UserId,
            keychainConfig: ApplePersistenceConfig,
            userAgent: String,
            certificatePinning: CertificatePinning,
            diagnostics: ReceivingDiagnostics? = null,
            block: suspend (AppleAccountRuntime) -> T,
        ): Either<AccountRuntimeFailure, T> {
            val lease = when (val acquired = AppleAccountRuntimeOwner(rootPath).tryAcquire(userId)) {
                is Either.Left -> return acquired
                is Either.Right -> acquired.value
            }
            var runtime: AppleAccountRuntime? = null
            return try {
                val prepared = ApplePreparedAccountStore(rootPath, userId).read(lease)
                    ?: return Either.Left(AccountRuntimeFailure.NOT_PREPARED)
                val account = AppleAccountRuntime(prepared, lease, "${appleAccountPath(rootPath, userId)}/storage/files")
                    .also { runtime = it }
                Either.Right(account.run(rootPath, keychainConfig, userAgent, certificatePinning, block))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                diagnostics?.failed(failure)
                kaliumLogger.w("Prepared account runtime failed", failure)
                val reason = if (failure is ExistingDatabaseException && failure.reason == ExistingDatabaseException.Reason.INCOMPATIBLE) {
                    AccountRuntimeFailure.INCOMPATIBLE
                } else AccountRuntimeFailure.STORAGE
                Either.Left(reason)
            } finally {
                if (runtime == null) lease.release()
            }
        }

        private fun requireCryptoFile(path: String) {
            val metadata = FileSystem.SYSTEM.metadataOrNull(path.toPath())
            check(metadata?.isRegularFile == true && (metadata.size ?: 0) > 0) { "Prepared crypto store is missing" }
        }
    }
}
