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
@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.cryptography

import com.wire.crypto.CommitBundle
import com.wire.crypto.ClientId
import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoLogLevel
import com.wire.crypto.CoreCryptoLogger
import com.wire.crypto.Database
import com.wire.crypto.DeviceId
import com.wire.crypto.EpochObserver
import com.wire.crypto.HistorySecret
import com.wire.crypto.HttpHeader
import com.wire.crypto.HttpMethod
import com.wire.crypto.HttpResponse
import com.wire.crypto.MlsTransport
import com.wire.crypto.MlsTransportData
import com.wire.crypto.PkiEnvironment
import com.wire.crypto.Uuid
import com.wire.crypto.X509CredentialAcquisition
import com.wire.crypto.X509CredentialAcquisitionConfiguration
import com.wire.crypto.new
import com.wire.crypto.newFromCredentialRef
import com.wire.kalium.cryptography.utils.toCrypto
import com.wire.kalium.cryptography.utils.toCryptography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import com.wire.crypto.MlsTransportException as CoreCryptoMlsTransportException
import com.wire.crypto.PkiEnvironmentHooks as CoreCryptoPkiEnvironmentHooks
import com.wire.crypto.PkiEnvironmentHooksException as CoreCryptoPkiEnvironmentHooksException

internal object CoreCryptoLoggerImpl : CoreCryptoLogger {
    override fun log(level: CoreCryptoLogLevel, message: String, context: String?) {
        when (level) {
            CoreCryptoLogLevel.TRACE -> kaliumLogger.v("$message. $context")
            CoreCryptoLogLevel.DEBUG -> kaliumLogger.d("$message. $context")
            CoreCryptoLogLevel.INFO -> kaliumLogger.i("$message. $context")
            CoreCryptoLogLevel.WARN -> kaliumLogger.w("$message. $context")
            CoreCryptoLogLevel.ERROR -> kaliumLogger.e("$message. $context")
            CoreCryptoLogLevel.OFF -> {
                // nop
            }
        }
    }
}

@Suppress("TooManyFunctions")
class CoreCryptoCentralImpl(
    private val cc: CoreCrypto,
    private val rootDir: String,
    private val database: Database
) : CoreCryptoCentral {

    // configurePkiEnvironment closes the previous native object, so it must never overlap an
    // operation or acquisition that still holds that environment.
    private val pkiEnvironmentMutex = Mutex()
    private var pkiEnvironment: PkiEnvironment? = null
    private var ownedResourcesClosed = false

    suspend fun <T> transaction(name: String, block: suspend (context: CoreCryptoContext) -> T): T = cc.transaction(name) {
        block(it)
    }

    override suspend fun mlsClient(
        clientId: CryptoQualifiedClientId,
        defaultCipherSuite: MLSCiphersuite,
        mlsTransporter: MLSTransporter,
        epochObserver: MLSEpochObserver,
        coroutineScope: CoroutineScope
    ): MLSClient {
        val coreCryptoClientId = clientId.toCoreCryptoClientId()
        val coreCryptoTransport = mlsTransporter.toCoreCryptoTransport()
        var clientIdOwnershipTransferred = false

        return try {
            cc.transaction("mlsInit") { context ->
                context.mlsInit(coreCryptoClientId, coreCryptoTransport)
            }

            cc.registerEpochObserver(
                coroutineScope,
                epochObserver = object : EpochObserver {
                    override suspend fun epochChanged(conversationId: ConversationId, epoch: ULong) {
                        try {
                            epochObserver.onEpochChange(Base64.encode(conversationId.copyBytes()), epoch)
                        } finally {
                            conversationId.close()
                        }
                    }
                }
            )

            MLSClientImpl.create(
                coreCrypto = cc,
                clientId = coreCryptoClientId,
                defaultCipherSuite = defaultCipherSuite.toCrypto(),
                onClose = ::close
            ).also {
                clientIdOwnershipTransferred = true
            }
        } finally {
            if (!clientIdOwnershipTransferred) coreCryptoClientId.close()
        }
    }

    override suspend fun proteusClient(): ProteusClient {
        return ProteusClientCoreCryptoImpl(cc, rootDir, ::close)
    }

    override suspend fun configurePkiEnvironment(hooks: PkiEnvironmentHooks) = pkiEnvironmentMutex.withLock {
        val newEnvironment = PkiEnvironment.new(hooks.toCoreCrypto(), database)
        try {
            cc.setPkiEnvironment(newEnvironment)
        } catch (exception: Exception) {
            newEnvironment.close()
            throw exception
        }

        val previousEnvironment = pkiEnvironment
        pkiEnvironment = newEnvironment
        previousEnvironment?.close()
        Unit
    }

    override suspend fun getPkiTrustAnchors(): List<CertificateChain> = pkiEnvironmentMutex.withLock {
        requirePkiEnvironment().getTrustAnchors()
    }

    override suspend fun reconcilePkiTrustAnchors(pemBundle: CertificateChain) = pkiEnvironmentMutex.withLock {
        val environment = requirePkiEnvironment()
        val plan = planPkiTrustAnchorReconciliation(
            currentAnchors = environment.getTrustAnchors(),
            desiredPemBundle = pemBundle
        )

        // Keep the update retry-safe: every desired root must exist before an obsolete one is
        // removed. If any addition fails, the previously usable set is left intact.
        plan.anchorsToAdd.forEach { environment.addTrustAnchor(it) }
        plan.fingerprintsToRemove.forEach { environment.removeTrustAnchor(it) }
    }

    override suspend fun addPkiIntermediateCertificate(pem: CertificateChain) = pkiEnvironmentMutex.withLock {
        requirePkiEnvironment().addIntermediateCert(pem)
    }

    override suspend fun startX509CredentialAcquisition(
        config: X509CredentialAcquisitionConfig,
        existingCredentialRef: CryptoCredentialRef?
    ): CryptoCredential = pkiEnvironmentMutex.withLock {
        try {
            val validityPeriodSeconds = config.validity.inWholeSeconds
            require(validityPeriodSeconds > 0) { "X509 credential validity must be positive" }
            val nativeClientId = config.clientId.toCoreCryptoClientId()
            val nativeConfig = try {
                X509CredentialAcquisitionConfiguration(
                    acmeDirectoryUrl = config.acmeDirectoryUrl,
                    cipherSuite = config.cipherSuite.toCrypto(),
                    displayName = config.displayName,
                    clientId = nativeClientId,
                    handle = config.handle,
                    domain = config.clientId.userId.domain,
                    team = config.teamId,
                    validityPeriodSecs = validityPeriodSeconds.toULong()
                )
            } catch (throwable: Throwable) {
                nativeClientId.close()
                throw throwable
            }
            val acquisition = existingCredentialRef?.let {
                X509CredentialAcquisition.newFromCredentialRef(
                    pkiEnvironment = requirePkiEnvironment(),
                    config = nativeConfig,
                    credentialRef = it.unwrap()
                )
            } ?: X509CredentialAcquisition(requirePkiEnvironment(), nativeConfig)
            acquisition.finalizeCredential()
        } finally {
            existingCredentialRef?.close()
        }
    }

    override suspend fun installCredential(credential: CryptoCredential): CryptoCredentialRef {
        try {
            val nativeCredential = credential.unwrap()
            return cc.transaction("installCredential") {
                CryptoCredentialRefImpl(it.addCredential(nativeCredential))
            }
        } finally {
            credential.close()
        }
    }

    override suspend fun checkCredentials() = pkiEnvironmentMutex.withLock {
        cc.transaction("checkCredentials") {
            it.checkCredentials()
        }
    }

    private suspend fun X509CredentialAcquisition.finalizeCredential(): CryptoCredential {
        try {
            return CryptoCredentialImpl(finalize())
        } finally {
            close()
        }
    }

    private fun requirePkiEnvironment(): PkiEnvironment =
        checkNotNull(pkiEnvironment) { "PKI environment has not been configured" }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun exportDatabaseCopy(destinationPath: String) {
        try {
            com.wire.crypto.exportDatabaseCopy(database, destinationPath)
        } catch (exception: Exception) {
            kaliumLogger.e("Exporting crypto database copy failed: $exception")
            throw exception
        }
    }

    override suspend fun close() = pkiEnvironmentMutex.withLock {
        if (!ownedResourcesClosed) {
            try {
                cc.close()
            } finally {
                closeOwnedResources()
            }
        }
    }

    private fun closeOwnedResources() {
        if (ownedResourcesClosed) return
        ownedResourcesClosed = true
        val environment = pkiEnvironment
        pkiEnvironment = null
        try {
            environment?.close()
        } finally {
            database.close()
        }
    }

    companion object {
        const val KEYSTORE_NAME = "keystore"
    }
}

internal fun CryptoQualifiedClientId.toCoreCryptoClientId(): ClientId {
    val deviceId = requireNotNull(value.toULongOrNull(radix = CLIENT_ID_RADIX)) {
        "Client id must be an unsigned hexadecimal value: $value"
    }
    val nativeUserId = Uuid(userId.value)
    val nativeDeviceId = try {
        DeviceId(deviceId)
    } catch (throwable: Throwable) {
        nativeUserId.close()
        throw throwable
    }
    return try {
        ClientId(
            userId = nativeUserId,
            deviceId = nativeDeviceId,
            domain = userId.domain
        )
    } finally {
        nativeUserId.close()
        nativeDeviceId.close()
    }
}

private fun MLSTransporter.toCoreCryptoTransport(): MlsTransport = object : MlsTransport {
    override suspend fun prepareForTransport(historySecret: HistorySecret): MlsTransportData {
        return try {
            this@toCoreCryptoTransport.prepareForTransport(
                MLSHistorySecret(
                    clientId = historySecret.clientId.toCryptography(),
                    data = historySecret.data
                )
            )
        } finally {
            historySecret.destroy()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
        try {
            try {
                this@toCoreCryptoTransport.sendCommitBundle(commitBundle.toCryptography())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: MlsMessageRejectedException) {
                throw CoreCryptoMlsTransportException.MessageRejected(exception.reason)
            } catch (exception: Exception) {
                throw CoreCryptoMlsTransportException.MessageRejected(
                    exception.message ?: "MLS delivery service rejected the commit"
                )
            }
        } finally {
            commitBundle.destroy()
        }
    }
}

private fun PkiEnvironmentHooks.toCoreCrypto(): CoreCryptoPkiEnvironmentHooks = object : CoreCryptoPkiEnvironmentHooks {
    override suspend fun httpRequest(
        method: HttpMethod,
        url: String,
        headers: List<HttpHeader>,
        body: ByteArray
    ): HttpResponse = mapPkiHookException {
        this@toCoreCrypto.httpRequest(
            method = method.toCryptography(),
            url = url,
            headers = headers.map { PkiHttpHeader(it.name, it.value) },
            body = body
        ).let { response ->
            HttpResponse(
                status = response.status,
                headers = response.headers.map { HttpHeader(it.name, it.value) },
                body = response.body
            )
        }
    }

    override suspend fun authenticate(
        idp: String,
        keyAuth: String,
        acmeAud: String,
        // Only Web needs this to resume after leaving and returning to the page.
        @Suppress("UNUSED_PARAMETER") acquisitionSnapshot: ByteArray
    ): String = mapPkiHookException {
        this@toCoreCrypto.authenticate(idp, keyAuth, acmeAud)
    }

    override suspend fun getBackendNonce(): String = mapPkiHookException {
        this@toCoreCrypto.getBackendNonce()
    }

    override suspend fun fetchBackendAccessToken(dpop: String): String = mapPkiHookException {
        this@toCoreCrypto.fetchBackendAccessToken(dpop)
    }
}

private fun HttpMethod.toCryptography(): PkiHttpMethod = when (this) {
    HttpMethod.GET -> PkiHttpMethod.GET
    HttpMethod.POST -> PkiHttpMethod.POST
    HttpMethod.PUT -> PkiHttpMethod.PUT
    HttpMethod.DELETE -> PkiHttpMethod.DELETE
    HttpMethod.PATCH -> PkiHttpMethod.PATCH
    HttpMethod.HEAD -> PkiHttpMethod.HEAD
}

private suspend fun <T> mapPkiHookException(block: suspend () -> T): T = try {
    block()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    throw CoreCryptoPkiEnvironmentHooksException.Exception(
        exception.message ?: "PKI environment hook failed"
    )
}

private const val CLIENT_ID_RADIX = 16
