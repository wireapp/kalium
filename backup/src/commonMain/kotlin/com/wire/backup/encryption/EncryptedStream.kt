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
package com.wire.backup.encryption

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13
import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.ionspin.kotlin.crypto.secretstream.SecretStreamCorruptedOrTamperedDataException
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_ABYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
import com.ionspin.kotlin.crypto.stream.crypto_stream_chacha20_KEYBYTES
import com.wire.backup.hash.HASH_MEM_LIMIT
import com.wire.backup.hash.HASH_OPS_LIMIT
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import kotlin.random.Random
import kotlin.random.nextUBytes

/**
 * Provides functions to encrypt and decrypt streams of data.
 * Streams of data: big amounts of data, that can be split into smaller chunks / messages.
 * For example, a huge file can be read in chunks, encrypted in chunks and then decrypted in chunks.
 * This is done using [Source] and [Sink].
 */
internal interface EncryptedStream<AuthenticationData> {
    /**
     * Encrypts the [source] data using the provided [authenticationData].
     * The result is fed into the [outputSink].
     * @return a [UByteArray] containing the extra data that might be needed for decryption depending on the implementation.
     * @see decrypt
     */
    suspend fun encrypt(source: Source, outputSink: Sink, authenticationData: AuthenticationData)

    /**
     * Decrypts the [source] data using the provided [authenticationData].
     * The result is fed into the [outputSink].
     * @see encrypt
     */
    suspend fun decrypt(
        source: Source,
        outputSink: Sink,
        authenticationData: AuthenticationData,
    ): DecryptionResult

    /**
     * Implementation of [EncryptedStream] that relies on Libsodium's
     * [SecretStream](https://libsodium.gitbook.io/doc/secret-key_cryptography/secretstream).
     * It will encrypt the whole [Source] into an output [Sink] in smaller messages of [INDIVIDUAL_PLAINTEXT_MESSAGE_SIZE].
     */
    companion object XChaCha20Poly1305 : EncryptedStream<XChaChaPoly1305AuthenticationData> {
        const val XCHACHA_20_POLY_1305_HEADER_LENGTH = 24
        private const val KEY_LENGTH = crypto_stream_chacha20_KEYBYTES
        private const val INDIVIDUAL_PLAINTEXT_MESSAGE_SIZE = 4096L
        private val INDIVIDUAL_ENCRYPTED_MESSAGE_SIZE = INDIVIDUAL_PLAINTEXT_MESSAGE_SIZE + crypto_secretstream_xchacha20poly1305_ABYTES

        override suspend fun encrypt(source: Source, outputSink: Sink, authenticationData: XChaChaPoly1305AuthenticationData) {
            initializeLibSodiumIfNeeded()
            val key = generateChaCha20Key(authenticationData)
            val stateHeader = SecretStream.xChaCha20Poly1305InitPush(key)
            val state = stateHeader.state
            val chaChaHeader = stateHeader.header
            val readBuffer = Buffer()
            val output = outputSink.buffer()
            output.write(chaChaHeader.toByteArray())

            // iterate with stuff
            var readBytes: Long
            var isTheLastMessage = false
            while (!isTheLastMessage) {
                readBytes = source.read(readBuffer, INDIVIDUAL_PLAINTEXT_MESSAGE_SIZE)
                if (readBytes <= 0L) break // Nothing else to read
                isTheLastMessage = readBytes < INDIVIDUAL_PLAINTEXT_MESSAGE_SIZE

                val appendingTag = if (isTheLastMessage) {
                    crypto_secretstream_xchacha20poly1305_TAG_FINAL
                } else {
                    crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
                }

                val plainTextMessage = readBuffer.readByteArray(readBytes).toUByteArray()
                val encryptedMessage = SecretStream.xChaCha20Poly1305Push(
                    state = state,
                    message = plainTextMessage,
                    associatedData = authenticationData.additionalData,
                    tag = appendingTag.toUByte(),
                )
                output.write(encryptedMessage.toByteArray())
            }
            source.close()
            output.close()
            outputSink.close()
        }

        override suspend fun decrypt(
            source: Source,
            outputSink: Sink,
            authenticationData: XChaChaPoly1305AuthenticationData,
        ): DecryptionResult {
            initializeLibSodiumIfNeeded()
            var decryptedDataSize = 0L
            val outputBuffer = outputSink.buffer()
            val readBuffer = Buffer()
            val key = generateChaCha20Key(authenticationData)
            return try {
                val headerBuffer = Buffer()
                source.read(headerBuffer, XCHACHA_20_POLY_1305_HEADER_LENGTH.toLong())
                val stateHeader = SecretStream.xChaCha20Poly1305InitPull(key, headerBuffer.readByteArray().toUByteArray())
                var hasReadLastMessage = false
                while (!hasReadLastMessage) {
                    val readBytes = source.read(readBuffer, INDIVIDUAL_ENCRYPTED_MESSAGE_SIZE)
                    if (readBytes <= 0) break
                    val encryptedData = readBuffer.readByteArray().toUByteArray()
                    val (decryptedData, tag) = SecretStream.xChaCha20Poly1305Pull(
                        stateHeader.state,
                        encryptedData,
                        authenticationData.additionalData
                    )
                    decryptedDataSize += decryptedData.size
                    outputBuffer.write(decryptedData.toByteArray())
                    val isEndOfEncryptedStream = tag.toInt() == crypto_secretstream_xchacha20poly1305_TAG_FINAL
                    hasReadLastMessage = isEndOfEncryptedStream
                }
                source.close()
                outputBuffer.close()
                outputSink.close()
                DecryptionResult.Success
            } catch (tamperedException: SecretStreamCorruptedOrTamperedDataException) {
                DecryptionResult.Failure.AuthenticationFailure
            }
        }

        private fun generateChaCha20Key(authData: XChaChaPoly1305AuthenticationData): UByteArray = PasswordHash.pwhash(
            outputLength = KEY_LENGTH,
            password = authData.passphrase,
            salt = authData.salt,
            opsLimit = authData.hashOpsLimit,
            memLimit = authData.hashMemLimit,
            algorithm = crypto_pwhash_argon2id_ALG_ARGON2ID13
        )
    }
}

internal sealed interface DecryptionResult {
    data object Success : DecryptionResult
    data object Failure : DecryptionResult {
        /**
         * Wrong passphrase, salt, header, or additional data
         */
        data object AuthenticationFailure : DecryptionResult
        data class Unknown(val message: String) : DecryptionResult
    }
}

/**
 * @param passphrase the password created by the user when encrypting
 * @param salt the random bytes to spice things up, can be created with [XChaChaPoly1305AuthenticationData.newSalt].
 * @param additionalData extra data that can be used and can be validated
 * together with the encrypted data.
 *
 * It _is_ optional and an empty array can be used, if no extra plaintext data needs to be validated.
 *
 * For the purposes of backup, we can use a hash of the non-encrypted
 * bytes of the file as additional data. This way, if these bytes were tempered with, the decryption
 * will also fail. The idea is to do not trust any fruit from the poisoned tree.
 */
internal data class XChaChaPoly1305AuthenticationData(
    val passphrase: String,
    val salt: UByteArray,
    val additionalData: UByteArray = ubyteArrayOf(),
    val hashOpsLimit: ULong = HASH_OPS_LIMIT,
    val hashMemLimit: Int = HASH_MEM_LIMIT,
) {
    companion object {
        private const val SALT_LENGTH = crypto_pwhash_SALTBYTES
        fun newSalt() = Random.nextUBytes(SALT_LENGTH).toUByteArray()
    }
}
