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

import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoCallbacks
import com.wire.crypto.client.Ciphersuites
import com.wire.crypto.coreCryptoDeferredInit
import com.wire.kalium.cryptography.MLSClientImpl.Companion.toCrlRegistration
import com.wire.kalium.cryptography.exceptions.CryptographyException
import java.io.File

<<<<<<< HEAD
actual suspend fun coreCryptoCentral(rootDir: String, databaseKey: String): CoreCryptoCentral {
    val path = "$rootDir/${CoreCryptoCentralImpl.KEYSTORE_NAME}"
    File(rootDir).mkdirs()
    val coreCrypto = coreCryptoDeferredInit(path, databaseKey, Ciphersuites.DEFAULT.lower(), null)
    coreCrypto.setCallbacks(Callbacks())
    return CoreCryptoCentralImpl(coreCrypto, rootDir)
=======
actual suspend fun coreCryptoCentral(
    rootDir: String,
    databaseKey: String
): CoreCryptoCentral {
    val path = "$rootDir/${CoreCryptoCentralImpl.KEYSTORE_NAME}"
    File(rootDir).mkdirs()
    val coreCrypto = coreCryptoDeferredInit(
        path = path,
        key = databaseKey,
        ciphersuites = emptyList(),
        nbKeyPackage = null
    )
    coreCrypto.setCallbacks(Callbacks())
    return CoreCryptoCentralImpl(
        cc = coreCrypto,
        rootDir = rootDir
    )
>>>>>>> d8ec03ef73 (feat: fetch MLS config when not available locally (#2740))
}

private class Callbacks : CoreCryptoCallbacks {

    override fun authorize(conversationId: ByteArray, clientId: ClientId): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }

    override fun clientIsExistingGroupUser(
        conversationId: ConversationId,
        clientId: ClientId,
        existingClients: List<ClientId>,
        parentConversationClients: List<ClientId>?
    ): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }

    override fun userAuthorize(
        conversationId: ConversationId,
        externalClientId: ClientId,
        existingClients: List<ClientId>
    ): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }
}

<<<<<<< HEAD
class CoreCryptoCentralImpl(private val cc: CoreCrypto, private val rootDir: String) : CoreCryptoCentral {
    fun getCoreCrypto() = cc

    override suspend fun mlsClient(clientId: CryptoQualifiedClientId): MLSClient {
        cc.mlsInit(clientId.toString().encodeToByteArray(), Ciphersuites.DEFAULT.lower(), null)
        return MLSClientImpl(cc)
=======
class CoreCryptoCentralImpl(
    private val cc: CoreCrypto,
    private val rootDir: String
) : CoreCryptoCentral {
    fun getCoreCrypto() = cc

    override suspend fun mlsClient(
        clientId: CryptoQualifiedClientId,
        cipherSuite: Ciphersuites,
        defaultCipherSuite: UShort
    ): MLSClient {
        cc.mlsInit(clientId.toString().encodeToByteArray(), cipherSuite, null)
        return MLSClientImpl(cc, defaultCipherSuite)
>>>>>>> d8ec03ef73 (feat: fetch MLS config when not available locally (#2740))
    }

    override suspend fun mlsClient(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt,
        defaultCipherSuite: UShort
    ): MLSClient {
        // todo: use DPs list from here, and return alongside with the mls client
        cc.e2eiMlsInitOnly(
            (enrollment as E2EIClientImpl).wireE2eIdentity,
            certificateChain, newMLSKeyPackageCount
        )
<<<<<<< HEAD
        return MLSClientImpl(cc)
=======
        return MLSClientImpl(cc, defaultCipherSuite)
>>>>>>> d8ec03ef73 (feat: fetch MLS config when not available locally (#2740))
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
        defaultCipherSuite: UShort
    ): E2EIClient {
        return E2EIClientImpl(
            cc.e2eiNewEnrollment(
                clientId.toString(),
                displayName,
                handle,
                teamId,
                expiry.inWholeSeconds.toUInt(),
<<<<<<< HEAD
                Ciphersuites.DEFAULT.lower().first()
=======
                defaultCipherSuite
>>>>>>> d8ec03ef73 (feat: fetch MLS config when not available locally (#2740))
            )

        )
    }

    override suspend fun registerTrustAnchors(pem: CertificateChain) {
        try {
            cc.e2eiRegisterAcmeCa(pem)
        } catch (e: CryptographyException) {
            kaliumLogger.w("Registering TrustAnchors failed")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration = try {
        toCrlRegistration(cc.e2eiRegisterCrl(url, crl))
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
            cc.e2eiRegisterIntermediateCa(pem)
        } catch (exception: Exception) {
            kaliumLogger.w("Registering IntermediateCa failed, exception: $exception")
        }
    }

    companion object {
        const val KEYSTORE_NAME = "keystore"
    }
}
