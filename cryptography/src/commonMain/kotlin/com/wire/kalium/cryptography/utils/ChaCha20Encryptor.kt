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

package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.ionspin.kotlin.crypto.secretstream.SecretStreamCorruptedOrTamperedDataException
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.kaliumLogger
import com.wire.kalium.cryptography.utils.LibsodiumInitializer.initializeLibsodiumIfNeeded
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Source
import okio.buffer

@OptIn(ExperimentalUnsignedTypes::class)
object ChaCha20Encryptor {

    private const val ENCRYPT_BUFFER_SIZE = 4096L

    suspend fun encryptBackupFile(
        backupDataSource: Source,
        outputSink: Sink,
        userId: CryptoUserID,
        passphrase: Passphrase
    ): Long {

        initializeLibsodiumIfNeeded()
        var encryptedDataSize = 0L
        val additionalInformation: UByteArray = BackupCoder.version.encodeToByteArray().toUByteArray()
        val outputBufferedSink = outputSink.buffer()

        try {
            val backupCoder = BackupCoder(
                userId = userId,
                passphrase = passphrase
            )
            val backupHeader = backupCoder.encodeHeader()
            val chaCha20Key = backupCoder.generateChaCha20Key(backupHeader)
            val backupHeaderData = backupHeader.toByteArray().also {
                if (it.isEmpty()) throw IllegalStateException("Backup header is empty")
            }

            // We append all the metadata unencrypted to the beginning of the backup file data
            outputBufferedSink.write(backupHeaderData)

            val stateAndHeader = SecretStream.xChaCha20Poly1305InitPush(chaCha20Key)
            val state = stateAndHeader.state
            val chachaHeader = stateAndHeader.header.toByteArray()

            // We write the ChaCha20 generated header prior to the encrypted backup file data
            outputBufferedSink.write(chachaHeader)

            val inputContentBuffer = Buffer()
            var byteCount: Long
            while (backupDataSource.read(inputContentBuffer, ENCRYPT_BUFFER_SIZE).also { byteCount = it } != -1L) {
                // We need to inform the end of the encryption stream with the TAG_FINAL
                val appendingTag = if (byteCount < ENCRYPT_BUFFER_SIZE) { // TODO: Find a better way to detect the end of the stream
                    crypto_secretstream_xchacha20poly1305_TAG_FINAL
                } else
                    crypto_secretstream_xchacha20poly1305_TAG_MESSAGE

                val dataToEncrypt = inputContentBuffer.readByteArray(byteCount).toUByteArray()
                val encryptedData = SecretStream.xChaCha20Poly1305Push(
                    state,
                    dataToEncrypt,
                    additionalInformation,
                    appendingTag.toUByte()
                )
                outputBufferedSink.write(encryptedData.toByteArray())
                encryptedDataSize += encryptedData.size
            }
        } catch (e: IOException) {
            kaliumLogger.e("There was an error encrypting backup data:\n $e}")
        } catch (e: IllegalStateException) {
            kaliumLogger.e("There was an error decoding backup header data. Stored hashed userId differs from the provided one:\n $e}")
        } catch (e: SecretStreamCorruptedOrTamperedDataException) {
            kaliumLogger.e("There was an error while encrypting the backup data with ChaCha20:\n $e}")
        } finally {
            backupDataSource.close()
            outputBufferedSink.close()
        }
        return encryptedDataSize
    }
}
