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

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.cryptography.swift.CoreCryptoWrapper
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("TooManyFunctions")
@OptIn(ExperimentalForeignApi::class)
class ProteusClientCoreCryptoImpl private constructor(
    private val wrapper: CoreCryptoWrapper
) : ProteusClient {

    private val mutex = Mutex()
    private val existingSessionsCache = mutableSetOf<CryptoSessionId>()

    override suspend fun close() {
        // CoreCrypto wrapper is managed by CoreCryptoCentral
    }

    override suspend fun <R> transaction(
        name: String,
        block: suspend (context: ProteusCoreCryptoContext) -> R
    ): R {
        return wrapException {
            block(createProteusCoreCryptoContext())
        }
    }

    private fun createProteusCoreCryptoContext() = object : ProteusCoreCryptoContext {

        override suspend fun getLocalFingerprint(): String {
            return suspendCoroutine { continuation ->
                wrapper.proteusFingerprintWithCompletion { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!)
                    }
                }
            }
        }

        override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): String {
            return suspendCoroutine { continuation ->
                wrapper.proteusFingerprintRemoteWithSessionId(sessionId = sessionId.value) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!)
                    }
                }
            }
        }

        override suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): String {
            // TODO: Implement when wrapper supports proteusFingerprintPrekeybundle
            return ""
        }

        override suspend fun newLastResortPreKey(): PreKeyCrypto {
            val id = suspendCoroutine<UShort> { continuation ->
                wrapper.proteusLastResortPrekeyIdWithCompletion { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result)
                    }
                }
            }
            val pkb = suspendCoroutine<ByteArray> { continuation ->
                wrapper.proteusLastResortPrekeyWithCompletion { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.toByteArray())
                    }
                }
            }
            return PreKeyCrypto(id.toInt(), pkb.encodeBase64())
        }

        override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean = mutex.withLock {
            if (existingSessionsCache.contains(sessionId)) {
                return@withLock true
            }

            suspendCoroutine { continuation ->
                wrapper.proteusSessionExistsWithSessionId(sessionId = sessionId.value) { result, error ->
                    if (error != null) {
                        continuation.resume(false)
                    } else {
                        if (result) {
                            existingSessionsCache.add(sessionId)
                        }
                        continuation.resume(result)
                    }
                }
            }
        }

        override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
            suspendCoroutine { continuation ->
                wrapper.proteusSessionFromPrekeyWithSessionId(
                    sessionId = sessionId.value,
                    prekey = preKeyCrypto.pkb.decodeBase64Bytes().toNSData()
                ) { error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
            return suspendCoroutine { continuation ->
                wrapper.proteusEncryptWithSessionId(
                    sessionId = sessionId.value,
                    plaintext = message.toNSData()
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.toByteArray())
                    }
                }
            }
        }

        override suspend fun encryptBatched(
            message: ByteArray,
            sessionIds: List<CryptoSessionId>
        ): Map<CryptoSessionId, ByteArray> {
            return suspendCoroutine { continuation ->
                wrapper.proteusEncryptBatchedWithSessions(
                    sessions = sessionIds.map { it.value },
                    plaintext = message.toNSData()
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val resultMap = result as? Map<String, NSData> ?: emptyMap()
                        val mappedResult = resultMap.mapNotNull { entry ->
                            CryptoSessionId.fromEncodedString(entry.key)?.let { sessionId ->
                                sessionId to entry.value.toByteArray()
                            }
                        }.toMap()
                        continuation.resume(mappedResult)
                    }
                }
            }
        }

        override suspend fun encryptWithPreKey(
            message: ByteArray,
            preKeyCrypto: PreKeyCrypto,
            sessionId: CryptoSessionId
        ): ByteArray {
            // First create session from prekey
            suspendCoroutine { continuation ->
                wrapper.proteusSessionFromPrekeyWithSessionId(
                    sessionId = sessionId.value,
                    prekey = preKeyCrypto.pkb.decodeBase64Bytes().toNSData()
                ) { error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
            // Then encrypt
            return encrypt(message, sessionId)
        }

        override suspend fun deleteSession(sessionId: CryptoSessionId) {
            existingSessionsCache.remove(sessionId)
            suspendCoroutine { continuation ->
                wrapper.proteusSessionDeleteWithSessionId(sessionId = sessionId.value) { error ->
                    if (error != null) {
                        kaliumLogger.w("Failed to delete session: ${error.localizedDescription}")
                    }
                    continuation.resume(Unit)
                }
            }
        }

        override suspend fun <T : Any> decryptMessage(
            sessionId: CryptoSessionId,
            message: ByteArray,
            handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
        ): T {
            // Try to get existing session first, if not create from message
            val sessionExists = doesSessionExist(sessionId)

            val decrypted = if (sessionExists) {
                // Decrypt with existing session
                suspendCoroutine<ByteArray> { continuation ->
                    wrapper.proteusDecryptWithSessionId(
                        sessionId = sessionId.value,
                        ciphertext = message.toNSData()
                    ) { result, error ->
                        if (error != null) {
                            continuation.resumeWithException(error.toKotlinException())
                        } else {
                            continuation.resume(result!!.toByteArray())
                        }
                    }
                }
            } else {
                // Create session from message and decrypt
                suspendCoroutine<ByteArray> { continuation ->
                    wrapper.proteusSessionFromMessageWithSessionId(
                        sessionId = sessionId.value,
                        envelope = message.toNSData()
                    ) { result, error ->
                        if (error != null) {
                            continuation.resumeWithException(error.toKotlinException())
                        } else {
                            existingSessionsCache.add(sessionId)
                            continuation.resume(result!!.toByteArray())
                        }
                    }
                }
            }

            return handleDecryptedMessage(decrypted)
        }
    }

    override suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto> {
        return wrapException {
            from.until(from + count).map { id ->
                val pkb = suspendCoroutine<ByteArray> { continuation ->
                    wrapper.proteusNewPrekeyWithPrekeyId(prekeyId = id.toUShort()) { result, error ->
                        if (error != null) {
                            continuation.resumeWithException(error.toKotlinException())
                        } else {
                            continuation.resume(result!!.toByteArray())
                        }
                    }
                }
                PreKeyCrypto(id, pkb.encodeBase64())
            }
        }
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto {
        return wrapException {
            val id = suspendCoroutine<UShort> { continuation ->
                wrapper.proteusLastResortPrekeyIdWithCompletion { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result)
                    }
                }
            }
            val pkb = suspendCoroutine<ByteArray> { continuation ->
                wrapper.proteusLastResortPrekeyWithCompletion { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.toByteArray())
                    }
                }
            }
            PreKeyCrypto(id.toInt(), pkb.encodeBase64())
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private inline fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, e)
        }
    }

    companion object {

        @Suppress("UnusedParameter")
        suspend fun create(wrapper: CoreCryptoWrapper, rootDir: String): ProteusClientCoreCryptoImpl {
            // Initialize Proteus
            suspendCoroutine { continuation ->
                wrapper.proteusInitWithCompletion { error ->
                    if (error != null) {
                        kaliumLogger.e("Failed to initialize Proteus: ${error.localizedDescription}")
                    }
                    continuation.resume(Unit)
                }
            }
            return ProteusClientCoreCryptoImpl(wrapper)
        }
    }
}
