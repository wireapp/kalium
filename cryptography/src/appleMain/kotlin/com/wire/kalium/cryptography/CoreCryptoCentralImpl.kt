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

import com.wire.kalium.cryptography.swift.CoreCryptoLoggerWrapper
import com.wire.kalium.cryptography.swift.CoreCryptoWrapper
import com.wire.kalium.cryptography.swift.E2eiEnrollmentWrapper
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.create
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration

@OptIn(ExperimentalForeignApi::class)
actual suspend fun coreCryptoCentral(
    rootDir: String,
    passphrase: ByteArray
): CoreCryptoCentral {
    val path = "$rootDir/${CoreCryptoCentralImpl.KEYSTORE_NAME}"

    // Create the root directory if it doesn't exist
    NSFileManager.defaultManager.createDirectoryAtPath(
        rootDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )

    // Setup logger
    @Suppress("MagicNumber")
    val logger = CoreCryptoLoggerWrapper { level, message, context ->
        when (level.toInt()) {
            1 -> kaliumLogger.v("$message. $context")
            2 -> kaliumLogger.d("$message. $context")
            3 -> kaliumLogger.i("$message. $context")
            4 -> kaliumLogger.w("$message. $context")
            5 -> kaliumLogger.e("$message. $context")
        }
    }
    CoreCryptoWrapper.setLogger(logger, level = 4) // WARN level

    val wrapper = suspendCoroutine { continuation ->
        CoreCryptoWrapper.createWithKeystorePath(
            keystorePath = path,
            passphrase = passphrase.toNSData()
        ) { result, error ->
            if (error != null) {
                continuation.resumeWithException(error.toKotlinException())
            } else {
                continuation.resume(result!!)
            }
        }
    }

    return CoreCryptoCentralImpl(wrapper, rootDir)
}

@OptIn(ExperimentalForeignApi::class)
class CoreCryptoCentralImpl(
    internal val wrapper: CoreCryptoWrapper,
    private val rootDir: String
) : CoreCryptoCentral {

    override suspend fun mlsClient(
        clientId: CryptoQualifiedClientId,
        allowedCipherSuites: List<MLSCiphersuite>,
        defaultCipherSuite: MLSCiphersuite,
        mlsTransporter: MLSTransporter,
        epochObserver: MLSEpochObserver,
        coroutineScope: CoroutineScope
    ): MLSClient {
        // Initialize MLS
        suspendCoroutine { continuation ->
            wrapper.mlsInitWithClientId(
                clientId = clientId.toString().encodeToByteArray().toNSData(),
                ciphersuites = allowedCipherSuites.map { it.toTag() }
            ) { error ->
                if (error != null) {
                    kaliumLogger.e("MLSClient initialization exception: ${error.localizedDescription}")
                }
                continuation.resume(Unit)
            }
        }

        return MLSClientImpl(wrapper, defaultCipherSuite)
    }

    override suspend fun mlsClient(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt,
        defaultCipherSuite: MLSCiphersuite,
        mlsTransporter: MLSTransporter,
        epochObserver: MLSEpochObserver,
        coroutineScope: CoroutineScope
    ): MLSClient {
        val enrollmentWrapper = (enrollment as E2EIClientImpl).enrollmentWrapper

        suspendCoroutine { continuation ->
            wrapper.e2eiMlsInitOnlyWithEnrollment(
                enrollment = enrollmentWrapper,
                certificateChain = certificateChain,
                nbKeyPackage = newMLSKeyPackageCount.toInt()
            ) { _, error ->
                if (error != null) {
                    kaliumLogger.e("E2EI MLSClient initialization exception: ${error.localizedDescription}")
                }
                continuation.resume(Unit)
            }
        }

        return MLSClientImpl(wrapper, defaultCipherSuite)
    }

    override suspend fun proteusClient(): ProteusClient {
        return ProteusClientCoreCryptoImpl.create(wrapper, rootDir)
    }

    override suspend fun newAcmeEnrollment(
        clientId: CryptoQualifiedClientId,
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: Duration,
        defaultCipherSuite: MLSCiphersuite
    ): E2EIClient {
        val enrollmentWrapper = suspendCoroutine<E2eiEnrollmentWrapper> { continuation ->
            wrapper.e2eiNewEnrollmentWithClientId(
                clientId = clientId.toString(),
                displayName = displayName,
                handle = handle,
                team = teamId,
                expirySec = expiry.inWholeSeconds.toUInt(),
                ciphersuite = defaultCipherSuite.toTag()
            ) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!)
                }
            }
        }

        return E2EIClientImpl(enrollmentWrapper)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun registerTrustAnchors(pem: CertificateChain) {
        try {
            suspendCoroutine { continuation ->
                wrapper.e2eiRegisterAcmeCaWithTrustAnchorPem(trustAnchorPem = pem) { error ->
                    if (error != null) {
                        kaliumLogger.w("Registering TrustAnchors failed: ${error.localizedDescription}")
                    }
                    continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            kaliumLogger.w("Registering TrustAnchors failed")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration {
        return try {
            suspendCoroutine { continuation ->
                wrapper.e2eiRegisterCrlWithCrlDp(
                    crlDp = url,
                    crlDer = crl.toNSData()
                ) { result, error ->
                    if (error != null) {
                        kaliumLogger.w("Registering Crl failed, exception: ${error.localizedDescription}")
                        continuation.resume(CrlRegistration(dirty = false, expiration = null))
                    } else {
                        continuation.resume(
                            CrlRegistration(
                                dirty = result!!.dirty(),
                                expiration = result.expiration().takeIf { it != 0UL }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            kaliumLogger.w("Registering Crl failed, exception: $e")
            CrlRegistration(dirty = false, expiration = null)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun registerIntermediateCa(pem: CertificateChain) {
        try {
            suspendCoroutine { continuation ->
                wrapper.e2eiRegisterIntermediateCaWithCertPem(certPem = pem) { _, error ->
                    if (error != null) {
                        kaliumLogger.w("Registering IntermediateCa failed, exception: ${error.localizedDescription}")
                    }
                    continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            kaliumLogger.w("Registering IntermediateCa failed, exception: $e")
        }
    }

    companion object {
        const val KEYSTORE_NAME = "keystore"
    }
}

// Extension functions for type conversion
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray = when {
    (this.length.toInt() == 0) -> ByteArray(0)
    this.bytes == null -> ByteArray(0)
    else -> memScoped {
        val pointer = bytes!!.reinterpret<ByteVar>()
        ByteArray(this@toByteArray.length.toInt()) { index ->
            pointer[index]
        }
    }
}

internal fun NSError.toKotlinException(): Exception {
    return Exception("${this.domain}: ${this.description}")
}

// MLS Ciphersuite tag values according to IANA MLS registry
internal fun MLSCiphersuite.toTag(): UShort = when (this) {
    MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> 0x0001u
    MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> 0x0002u
    MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> 0x0003u
    MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> 0x0004u
    MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> 0x0005u
    MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> 0x0006u
    MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> 0x0007u
}
