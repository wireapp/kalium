@file:OptIn(com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class)
@file:Suppress("NestedBlockDepth", "TooGenericExceptionCaught")

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.logic.service

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.MlsTransportResponse
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceCryptoRuntime
import com.wire.kalium.logic.service.api.ServiceFailure
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.logic.service.api.ServiceResult
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

@ExperimentalKaliumServiceApi
public enum class ServiceCryptoStorageMode {
    CREATE_NEW,
    RESTORE_EXISTING,
}

@ExperimentalKaliumServiceApi
public data class ServiceCryptoStorage(
    public val proteusRoot: Path,
    public val proteusPassphrase: ByteArray,
    public val mlsRoot: Path,
    public val mlsPassphrase: ByteArray,
    public val mode: ServiceCryptoStorageMode,
    public val allowedCipherSuites: List<MLSCiphersuite>,
    public val defaultCipherSuite: MLSCiphersuite,
) {
    init {
        require(proteusPassphrase.isNotEmpty()) { "Proteus database passphrase must not be empty" }
        require(mlsPassphrase.isNotEmpty()) { "MLS database passphrase must not be empty" }
        require(allowedCipherSuites.isNotEmpty()) { "At least one MLS ciphersuite is required" }
        require(defaultCipherSuite in allowedCipherSuites) { "Default MLS ciphersuite must be allowed" }
        val normalizedProteusRoot = proteusRoot.toAbsolutePath().normalize()
        val normalizedMlsRoot = mlsRoot.toAbsolutePath().normalize()
        require(normalizedProteusRoot != normalizedMlsRoot) {
            "Proteus and MLS stores must use separate identity-scoped roots"
        }
        require(!normalizedProteusRoot.startsWith(normalizedMlsRoot) && !normalizedMlsRoot.startsWith(normalizedProteusRoot)) {
            "Proteus and MLS stores must not be nested"
        }
    }
}

@ExperimentalKaliumServiceApi
public data class ServiceMlsEpoch(public val groupId: String, public val epoch: ULong)

/** Identity-local epoch fan-out shared by CoreCrypto, conference membership, and AVS. */
@ExperimentalKaliumServiceApi
public class WireMlsEpochBus : MLSEpochObserver {
    private val changes = MutableSharedFlow<ServiceMlsEpoch>(extraBufferCapacity = 64)
    private val latestEpochs = MutableStateFlow<Map<String, ULong>>(emptyMap())

    public fun observe(): Flow<ServiceMlsEpoch> = changes.asSharedFlow()

    internal fun observeGroups(groupIds: Set<String>): Flow<Unit> = latestEpochs
        .map { epochs -> groupIds.associateWith(epochs::get) }
        .distinctUntilChanged()
        .map { Unit }

    override suspend fun onEpochChange(groupId: String, epoch: ULong) {
        latestEpochs.update { it + (groupId to epoch) }
        changes.emit(ServiceMlsEpoch(groupId, epoch))
    }
}

/** Real CoreCrypto MLS transport backed by the authenticated Wire MLS API. */
@ExperimentalKaliumServiceApi
public class WireMlsTransporter(private val networkOwner: JvmServiceNetworkOwner) : MLSTransporter {
    override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse =
        networkOwner.requireNetwork().mlsMessageApi.sendMessage(mlsMessage).toTransportResult()

    override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse =
        networkOwner.requireNetwork().mlsMessageApi.sendCommitBundle(
            MLSMessageApi.CommitBundle(
                commitBundle.commit + commitBundle.groupInfoBundle.payload + (commitBundle.welcome ?: ByteArray(0)),
            ),
        ).toTransportResult()

    private fun NetworkResponse<*>.toTransportResult(): MlsTransportResponse = when (this) {
        is NetworkResponse.Success -> MlsTransportResponse.Success
        is NetworkResponse.Error -> {
            val exception = kException
            if (exception is KaliumException.InvalidRequestError && exception.isMlsStaleMessage()) {
                MlsTransportResponse.Retry
            } else {
                MlsTransportResponse.Abort(exception::class.simpleName ?: "MLS transport failed")
            }
        }
    }
}

/** Opens and owns durable Proteus and MLS CoreCrypto clients for one exact Wire identity. */
@ExperimentalKaliumServiceApi
public class WireServiceCryptoRuntime(
    private val expectedIdentity: ServiceIdentity,
    storage: ServiceCryptoStorage,
    private val transporter: MLSTransporter,
    public val epochBus: WireMlsEpochBus,
) : ServiceCryptoRuntime {
    private val lifecycleMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val proteusRoot = storage.proteusRoot
    private val mlsRoot = storage.mlsRoot
    private val mode = storage.mode
    private val allowedCipherSuites = storage.allowedCipherSuites.toList()
    private val defaultCipherSuite = storage.defaultCipherSuite
    private val proteusPassphrase = storage.proteusPassphrase.copyOf()
    private val mlsPassphrase = storage.mlsPassphrase.copyOf()
    private var proteusCentral: CoreCryptoCentral? = null
    private var mlsCentral: CoreCryptoCentral? = null
    private var proteusClient: ProteusClient? = null
    private var mlsClient: MLSClient? = null
    private var started = false
    private var terminal = false
    private var closed = false
    internal var afterStart: suspend () -> ServiceResult = { ServiceResult.Success }
    internal var beforeClose: suspend () -> ServiceResult = { ServiceResult.Success }

    override suspend fun start(identity: ServiceIdentity): ServiceResult = lifecycleMutex.withLock {
        if (closed || terminal) return@withLock ServiceResult.Failure(ServiceFailure.Crypto("Crypto runtime is closed"))
        if (started) return@withLock ServiceResult.Success
        if (identity != expectedIdentity) {
            return@withLock ServiceResult.Failure(ServiceFailure.Crypto("Crypto identity does not match service identity"))
        }
        try {
            validateStorage(proteusRoot, "Proteus")
            validateStorage(mlsRoot, "MLS")
            proteusCentral = coreCryptoCentral(proteusRoot.toString(), proteusPassphrase)
            proteusClient = checkNotNull(proteusCentral).proteusClient()
            mlsCentral = coreCryptoCentral(mlsRoot.toString(), mlsPassphrase)
            mlsClient = checkNotNull(mlsCentral).mlsClient(
                clientId = CryptoQualifiedClientId(
                    identity.clientId,
                    CryptoQualifiedID(identity.userId.value, identity.userId.domain),
                ),
                allowedCipherSuites = allowedCipherSuites,
                defaultCipherSuite = defaultCipherSuite,
                mlsTransporter = transporter,
                epochObserver = epochBus,
                coroutineScope = scope,
            )
            when (val result = afterStart()) {
                is ServiceResult.Failure -> {
                    failStartup()
                    result
                }
                ServiceResult.Success -> {
                    started = true
                    ServiceResult.Success
                }
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { failStartup() }
            throw cancellation
        } catch (failure: Throwable) {
            failStartup()
            ServiceResult.Failure(ServiceFailure.Crypto("Unable to open durable CoreCrypto state", failure))
        }
    }

    public suspend fun <Value> withProteus(
        name: String,
        block: suspend (ProteusCoreCryptoContext) -> Value,
    ): Value = requireNotNull(proteusClient) { "Proteus runtime is not started" }.transaction(name, block)

    public suspend fun <Value> withMls(
        name: String,
        block: suspend (MlsCoreCryptoContext) -> Value,
    ): Value = requireNotNull(mlsClient) { "MLS runtime is not started" }.transaction(name, block)

    override suspend fun close(): ServiceResult = lifecycleMutex.withLock {
        if (closed) return@withLock ServiceResult.Success
        when (val hook = beforeClose()) {
            is ServiceResult.Failure -> return@withLock hook
            ServiceResult.Success -> Unit
        }
        terminal = true
        val failure = closeClients()
        started = false
        if (hasOwnedCryptoResources()) {
            ServiceResult.Failure(ServiceFailure.Crypto("CoreCrypto close failed", failure))
        } else {
            closed = true
            scope.cancel()
            proteusPassphrase.fill(0)
            mlsPassphrase.fill(0)
            ServiceResult.Success
        }
    }

    private fun validateStorage(root: Path, name: String) {
        Files.createDirectories(root)
        val keystore = root.resolve(KEYSTORE_FILE)
        val identityMarker = root.resolve(IDENTITY_MARKER_FILE)
        val expectedMarker = serviceIdentityMarkerBytes(expectedIdentity)
        when (mode) {
            ServiceCryptoStorageMode.CREATE_NEW -> {
                require(!Files.exists(keystore)) {
                    "$name CoreCrypto store already exists but CREATE_NEW was requested"
                }
                if (!Files.exists(identityMarker)) {
                    try {
                        Files.write(
                            identityMarker,
                            expectedMarker,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                        )
                    } catch (failure: java.nio.file.FileAlreadyExistsException) {
                        // Another owner may have created the marker between the existence check and write.
                    }
                }
            }
            ServiceCryptoStorageMode.RESTORE_EXISTING -> {
                require(Files.exists(keystore)) {
                    "$name CoreCrypto store is missing but RESTORE_EXISTING was requested"
                }
                require(Files.exists(identityMarker)) {
                    "$name CoreCrypto store has no service identity marker"
                }
            }
        }
        require(Files.readAllBytes(identityMarker).contentEquals(expectedMarker)) {
            "$name CoreCrypto store belongs to a different Wire identity"
        }
    }

    private suspend fun closeClients(): Throwable? {
        var firstFailure: Throwable? = null
        val ownedMlsClient = mlsClient
        if (ownedMlsClient != null) {
            try {
                ownedMlsClient.close()
                mlsClient = null
                mlsCentral = null
            } catch (failure: Throwable) {
                firstFailure = failure
            }
        } else {
            val ownedMlsCentral = mlsCentral
            if (ownedMlsCentral != null) {
                try {
                    ownedMlsCentral.close()
                    mlsCentral = null
                } catch (failure: Throwable) {
                    firstFailure = failure
                }
            }
        }
        val ownedProteusClient = proteusClient
        if (ownedProteusClient != null) {
            try {
                ownedProteusClient.close()
                proteusClient = null
                proteusCentral = null
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        } else {
            val ownedProteusCentral = proteusCentral
            if (ownedProteusCentral != null) {
                try {
                    ownedProteusCentral.close()
                    proteusCentral = null
                } catch (failure: Throwable) {
                    if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
                }
            }
        }
        return firstFailure
    }

    private suspend fun failStartup() {
        try {
            beforeClose()
        } catch (_: Throwable) {
            // Preserve the original startup failure while still releasing owned crypto handles.
        }
        closeClients()
        terminal = true
        if (!hasOwnedCryptoResources()) {
            closed = true
            scope.cancel()
            proteusPassphrase.fill(0)
            mlsPassphrase.fill(0)
        }
        started = false
    }

    private fun hasOwnedCryptoResources(): Boolean =
        mlsClient != null || mlsCentral != null || proteusClient != null || proteusCentral != null

    private companion object {
        const val KEYSTORE_FILE = SERVICE_KEYSTORE_FILE
        const val IDENTITY_MARKER_FILE = SERVICE_IDENTITY_MARKER_FILE
    }
}

internal const val SERVICE_KEYSTORE_FILE: String = "keystore"
internal const val SERVICE_IDENTITY_MARKER_FILE: String = ".kalium-service-identity"

internal fun serviceIdentityMarkerBytes(identity: ServiceIdentity): ByteArray {
    val canonicalIdentity = listOf(
        identity.backendDomain,
        identity.userId.domain,
        identity.userId.value,
        identity.clientId,
    ).joinToString(separator = "\u0000")
    return MessageDigest.getInstance("SHA-256").digest(canonicalIdentity.encodeToByteArray())
}

internal fun bindProvisionedServiceStorage(root: Path, identity: ServiceIdentity) {
    Files.write(
        root.resolve(SERVICE_IDENTITY_MARKER_FILE),
        serviceIdentityMarkerBytes(identity),
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    )
}
