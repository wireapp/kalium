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

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13
import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.ionspin.kotlin.crypto.secretstream.SecretStreamCorruptedOrTamperedDataException
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_ABYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
import com.ionspin.kotlin.crypto.stream.crypto_stream_chacha20_KEYBYTES
import com.wire.backup.envelope.cryptography.BackupPassphrase
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import kotlin.random.Random
import kotlin.random.nextUBytes

internal interface EncryptedStream<AuthenticationData> {
    /**
     * Encrypts the [source] data using the provided [authenticationData].
     * The result is fed into the [outputSink].
     * @return a [UByteArray] containing the extra data that might be needed for decryption depending on the implementation.
     * @see decrypt
     */
    suspend fun encrypt(source: Source, outputSink: Sink, authenticationData: AuthenticationData): UByteArray

    /**
     * Decrypts the [source] data using the provided [authenticationData] and [encryptionHeader].
     * @param encryptionHeader the result of the [encrypt] function.
     * The result is fed into the [outputSink].
     * @see encrypt
     */
    suspend fun decrypt(
        source: Source,
        outputSink: Sink,
        authenticationData: AuthenticationData,
        encryptionHeader: UByteArray
    ): DecryptionResult

    companion object XChaCha20Poly1305 : EncryptedStream<XChaChaPoly1305AuthenticationData> {
        private const val KEY_LENGTH = crypto_stream_chacha20_KEYBYTES
        private const val INDIVIDUAL_PLAINTEXT_MESSAGE_SIZE = 4096L
        private val INDIVIDUAL_ENCRYPTED_MESSAGE_SIZE = INDIVIDUAL_PLAINTEXT_MESSAGE_SIZE + crypto_secretstream_xchacha20poly1305_ABYTES

        private suspend fun initializeLibSodiumIfNeeded() {
            if (!LibsodiumInitializer.isInitialized()) {
                LibsodiumInitializer.initialize()
            }
        }

        override suspend fun encrypt(source: Source, outputSink: Sink, authenticationData: XChaChaPoly1305AuthenticationData): UByteArray {
            initializeLibSodiumIfNeeded()
            val key = generateChaCha20Key(authenticationData)
            val stateHeader = SecretStream.xChaCha20Poly1305InitPush(key)
            val state = stateHeader.state
            val chaChaHeader = stateHeader.header
            val readBuffer = Buffer()
            val output = outputSink.buffer()

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
            return chaChaHeader
        }

        override suspend fun decrypt(
            source: Source,
            outputSink: Sink,
            authenticationData: XChaChaPoly1305AuthenticationData,
            encryptionHeader: UByteArray
        ): DecryptionResult {
            initializeLibSodiumIfNeeded()
            var decryptedDataSize = 0L
            val outputBuffer = outputSink.buffer()
            val readBuffer = Buffer()
            val key = generateChaCha20Key(authenticationData)
            return try {

                val stateHeader = SecretStream.xChaCha20Poly1305InitPull(key, encryptionHeader)
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
            password = authData.passphrase.value,
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
 * @param salt the random bytes to spice things up
 * @param additionalData extra data that can be used and can be validated
 * together with the encrypted data. For example, we can use a hash of the non-encrypted
 * bytes of the file as additional data. This way, if these bytes were tempered with, the decryption
 * will also fail. Don't trust the
 */
internal data class XChaChaPoly1305AuthenticationData(
    val passphrase: BackupPassphrase,
    val salt: UByteArray,
    val additionalData: UByteArray,
    val hashOpsLimit: ULong = HASH_OPS_LIMIT,
    val hashMemLimit: Int = HASH_MEM_LIMIT,
) {
    companion object {
        const val SALT_LENGTH = crypto_pwhash_SALTBYTES

        // crypto_pwhash_argon2i_OPSLIMIT_INTERACTIVE
        private const val HASH_OPS_LIMIT = 4UL

        // crypto_pwhash_argon2i_MEMLIMIT_INTERACTIVE
        private const val HASH_MEM_LIMIT = 33554432
        fun newSalt() = Random.nextUBytes(SALT_LENGTH)
    }
}
