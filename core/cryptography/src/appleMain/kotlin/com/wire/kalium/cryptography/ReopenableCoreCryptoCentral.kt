/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.cryptography

import com.wire.kalium.util.AccountResourceAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Stable Apple facade; only the native resource generation changes during account handoff. */
@Suppress("TooManyFunctions") // Implements the complete CoreCryptoCentral interface through one admission boundary.
class ReopenableCoreCryptoCentral(
    private val access: AccountResourceAccess,
    private val factory: suspend () -> CoreCryptoCentral,
) : CoreCryptoCentral {
    private val mutex = Mutex()
    private val callbacks = CoreCryptoCallbackContext(access)
    private var native: CoreCryptoCentral? = null
    private var hooks: PkiEnvironmentHooks? = null
    private var generation = 0L
    private var closed = false
    private var failure: Throwable? = null

    private suspend fun resource(): Pair<CoreCryptoCentral, Long> = mutex.withLock {
        check(!closed) { "Crypto owner is closed" }
        checkHealthy()
        val current = native ?: openResource().also { native = it }
        current to generation
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun openResource(): CoreCryptoCentral {
        val opened = try {
            factory()
        } catch (uncertain: CoreCryptoOpenCleanupException) {
            failure = uncertain
            throw uncertain
        }
        try {
            hooks?.let { opened.configurePkiEnvironment(callbacks.pki(it, generation)) }
        } catch (configurationFailure: Throwable) {
            withContext(NonCancellable) {
                try {
                    opened.close()
                } catch (cleanupFailure: Throwable) {
                    configurationFailure.addSuppressed(cleanupFailure)
                    failure = configurationFailure
                    native = opened
                }
            }
            throw configurationFailure
        }
        return opened
    }

    private fun checkHealthy() {
        check(failure == null) { "Native crypto cleanup could not be established" }
    }

    private suspend fun <T> use(block: suspend (CoreCryptoCentral) -> T): T = access.withAccess {
        callbacks.operation(generation) { block(resource().first) }
    }

    /** Call only after the account access boundary has drained all operations. */
    suspend fun suspendNativeResources() = callbacks.operation(generation) {
        mutex.withLock {
            checkHealthy()
            closeNative()
            generation++
        }
    }

    override suspend fun close() = access.withAccess {
        callbacks.operation(generation) {
            mutex.withLock {
                checkHealthy()
                closeNative()
                closed = true
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeNative() {
        try {
            native?.close()
            native = null
        } catch (cleanupFailure: Throwable) {
            failure = cleanupFailure
            throw cleanupFailure
        }
    }

    override suspend fun proteusClient(): ProteusClient {
        val client = Proteus()
        client.ready()
        return client
    }

    override suspend fun mlsClient(
        clientId: CryptoQualifiedClientId,
        defaultCipherSuite: MLSCiphersuite,
        mlsTransporter: MLSTransporter,
        epochObserver: MLSEpochObserver,
        coroutineScope: CoroutineScope,
    ): MLSClient {
        val client = Mls(clientId, defaultCipherSuite, mlsTransporter, epochObserver, coroutineScope)
        client.ready()
        return client
    }

    override suspend fun configurePkiEnvironment(hooks: PkiEnvironmentHooks) = use {
        mutex.withLock {
            it.configurePkiEnvironment(callbacks.pki(hooks, generation))
            this.hooks = hooks
        }
    }

    override suspend fun hasCredential(clientId: CryptoQualifiedClientId, cipherSuite: MLSCiphersuite) =
        use { it.hasCredential(clientId, cipherSuite) }
    override suspend fun getPkiTrustAnchors() = use { it.getPkiTrustAnchors() }
    override suspend fun addPkiTrustAnchors(pemBundle: CertificateChain) = use { it.addPkiTrustAnchors(pemBundle) }
    override suspend fun addPkiIntermediateCertificate(pem: CertificateChain) = use {
        it.addPkiIntermediateCertificate(pem)
    }
    override suspend fun startX509CredentialAcquisition(
        config: X509CredentialAcquisitionConfig,
        existingCredentialRef: CryptoCredentialRef?
    ) =
        use { it.startX509CredentialAcquisition(config, existingCredentialRef) }
    override suspend fun installCredential(credential: CryptoCredential) = use { it.installCredential(credential) }
    override suspend fun checkCredentials() = use { it.checkCredentials() }
    override suspend fun exportDatabaseCopy(destinationPath: String) = use { it.exportDatabaseCopy(destinationPath) }

    private abstract inner class Client<T> {
        private val clientMutex = Mutex()
        private var delegate: T? = null
        private var delegateGeneration = -1L
        protected abstract suspend fun create(central: CoreCryptoCentral): T
        suspend fun ready() = useClient { }
        protected suspend fun <R> useClient(block: suspend (T) -> R): R = access.withAccess {
            callbacks.operation(generation) {
                val (central, currentGeneration) = resource()
                val client = clientMutex.withLock {
                    if (delegateGeneration != currentGeneration) {
                        delegate = create(central)
                        delegateGeneration = currentGeneration
                    }
                    checkNotNull(delegate)
                }
                block(client)
            }
        }
    }

    private inner class Proteus : Client<ProteusClient>(), ProteusClient {
        override suspend fun create(central: CoreCryptoCentral) = central.proteusClient()
        override suspend fun close() = this@ReopenableCoreCryptoCentral.close()
        override suspend fun newPreKeysAuto(count: Int) = useClient { it.newPreKeysAuto(count) }
        override suspend fun newLastResortPreKey() = useClient { it.newLastResortPreKey() }
        override suspend fun <R> transaction(name: String, block: suspend (ProteusCoreCryptoContext) -> R): R = useClient {
            val context = currentCoroutineContext()
            // UniFFI invokes callbacks in a new coroutine; preserve admission for nested storage work.
            it.transaction(name) { transaction -> withContext(context + NonCancellable) { block(transaction) } }
        }
    }

    private inner class Mls(
        private val clientId: CryptoQualifiedClientId,
        private val suite: MLSCiphersuite,
        private val transport: MLSTransporter,
        private val observer: MLSEpochObserver,
        private val scope: CoroutineScope,
    ) : Client<MLSClient>(), MLSClient {
        override suspend fun create(central: CoreCryptoCentral) =
            central.mlsClient(clientId, suite, callbacks.transport(transport, generation), observer, scope)
        override fun getDefaultCipherSuite() = suite
        override suspend fun close() = this@ReopenableCoreCryptoCentral.close()
        override suspend fun initializeBasicCredential() = useClient { it.initializeBasicCredential() }
        override suspend fun getPublicKey() = useClient { it.getPublicKey() }
        override suspend fun getCredentialRef(credentialType: CredentialType) = useClient {
            it.getCredentialRef(credentialType)
        }
        override suspend fun getCredentialRefs(credentialType: CredentialType) = useClient {
            it.getCredentialRefs(credentialType)
        }
        override suspend fun getGroupState(groupId: MLSGroupId) = useClient { it.getGroupState(groupId) }
        override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<CryptoQualifiedID>) =
            useClient { it.getUserIdentities(groupId, users) }
        override suspend fun <R> transaction(name: String, block: suspend (MlsCoreCryptoContext) -> R): R = useClient {
            val context = currentCoroutineContext()
            it.transaction(name) { transaction -> withContext(context + NonCancellable) { block(transaction) } }
        }
    }
}
