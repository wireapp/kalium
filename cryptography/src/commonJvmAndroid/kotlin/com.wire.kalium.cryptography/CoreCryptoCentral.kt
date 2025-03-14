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
package com.wire.kalium.cryptography

import com.wire.crypto.Ciphersuites
import com.wire.crypto.ClientId
import com.wire.crypto.CommitBundle
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.CoreCryptoLogLevel
import com.wire.crypto.CoreCryptoLogger
import com.wire.crypto.MlsTransport
import com.wire.crypto.MlsTransportResponse
import com.wire.crypto.setLogger
import com.wire.crypto.setMaxLogLevel
import com.wire.kalium.cryptography.exceptions.CryptographyException
import com.wire.kalium.cryptography.utils.toCrypto
import com.wire.kalium.cryptography.utils.toCryptography
import java.io.File

actual suspend fun coreCryptoCentral(
    rootDir: String,
    databaseKey: String,
    mlsTransporter: MLSTransporter?
): CoreCryptoCentral {
    val path = "$rootDir/${CoreCryptoCentralImpl.KEYSTORE_NAME}"
    File(rootDir).mkdirs()
    val coreCrypto = CoreCrypto(
        keystore = path,
        databaseKey = databaseKey
    )

    setLogger(CoreCryptoLoggerImpl)
    setMaxLogLevel(CoreCryptoLogLevel.WARN)

    mlsTransporter?.let { transporter ->
        coreCrypto.provideTransport(object : MlsTransport {
            override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse {
                return transporter.sendCommitBundle(commitBundle.toCryptography()).toCrypto()
            }

            override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse {
                return transporter.sendMessage(mlsMessage).toCrypto()
            }
        })
    }

    return CoreCryptoCentralImpl(
        cc = coreCrypto,
        rootDir = rootDir
    )
}

private object CoreCryptoLoggerImpl : CoreCryptoLogger {
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

class CoreCryptoCentralImpl(
    private val cc: CoreCrypto,
    private val rootDir: String
) : CoreCryptoCentral {

    suspend fun transaction(block: suspend (context: CoreCryptoContext) -> Unit) = cc.transaction {
        block(it)
    }

    override suspend fun mlsClient(
        clientId: CryptoQualifiedClientId,
        allowedCipherSuites: List<MLSCiphersuite>,
        defaultCipherSuite: MLSCiphersuite
    ): MLSClient {
        try {
            cc.transaction { context ->
                context.mlsInit(
                    ClientId(clientId.toString()),
                    Ciphersuites(allowedCipherSuites.map { it.toCrypto() }.toSet()),
                )
            }
        } catch (e: CoreCryptoException) {
            kaliumLogger.e("MLSClient initialization exception: $e")
        }

        return MLSClientImpl(cc, defaultCipherSuite.toCrypto())
    }

    override suspend fun mlsClient(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt,
        defaultCipherSuite: MLSCiphersuite
    ): MLSClient {
        // todo: use DPs list from here, and return alongside with the mls client

        cc.transaction {
            it.e2eiMlsInitOnly(
                (enrollment as E2EIClientImpl).wireE2eIdentity,
                certificateChain,
                newMLSKeyPackageCount
            )
        }
        return MLSClientImpl(cc, defaultCipherSuite.toCrypto())
    }

    override suspend fun proteusClient(): ProteusClient {
        return ProteusClientCoreCryptoImpl(cc, rootDir)
    }

    override suspend fun newAcmeEnrollment(
        clientId: CryptoQualifiedClientId,
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: kotlin.time.Duration,
        defaultCipherSuite: MLSCiphersuite
    ): E2EIClient {
        return cc.transaction {
            E2EIClientImpl(
                it.e2eiNewEnrollment(
                    clientId.toString(),
                    displayName,
                    handle,
                    expiry.inWholeSeconds.toUInt(),
                    defaultCipherSuite.toCrypto(),
                    teamId,
                )
            )
        }
    }

    override suspend fun registerTrustAnchors(pem: CertificateChain) {
        try {
            cc.transaction {
                it.e2eiRegisterAcmeCA(pem)
            }
        } catch (e: CryptographyException) {
            kaliumLogger.w("Registering TrustAnchors failed")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration = try {
        cc.transaction {
            it.e2eiRegisterCRL(url, crl).toCryptography()
        }
    } catch (exception: Exception) {
        kaliumLogger.w("Registering Crl failed, exception: $exception")
        CrlRegistration(
            dirty = false,
            expiration = null
        )
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun registerIntermediateCa(pem: CertificateChain) {
        try {
            cc.transaction {
                it.e2eiRegisterIntermediateCA(pem)
            }
        } catch (exception: Exception) {
            kaliumLogger.w("Registering IntermediateCa failed, exception: $exception")
        }
    }

    companion object {
        const val KEYSTORE_NAME = "keystore"
    }
}
