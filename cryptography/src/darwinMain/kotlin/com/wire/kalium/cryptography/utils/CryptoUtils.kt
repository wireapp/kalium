/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.cryptography.exceptions.CryptographyException
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCKeySizeAES256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData {
    val outputBuffer = Buffer()
    val inputBuffer = Buffer()

    return outputBuffer.use { output ->
        inputBuffer.use { input ->
            input.write(data.data)
            encryptFileWithAES256(input, key, output)
        }
        EncryptedData(output.readByteArray())
    }
}

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData {
    val outputBuffer = Buffer()
    val inputBuffer = Buffer()

    return outputBuffer.use { output ->
        inputBuffer.use { input ->
            input.write(data.data)
            decryptFileWithAES256(input, output, secretKey)
        }
        PlainData(output.readByteArray())
    }
}

actual fun encryptFileWithAES256(assetDataSource: Source, key: AES256Key, outputSink: Sink): Long {
    try {
        val iv = generateRandomData(kCCBlockSizeAES128.toInt())
        val plainData = assetDataSource.buffer().readByteArray()
        val encryptedBuffer = ByteArray(plainData.size + kCCBlockSizeAES128.toInt())

        // TODO avoid read whole file into memory by using streaming or block based API
        return memScoped {
            val bytesCopied = alloc<ULongVar>()
            val status = key.data.usePinned { key ->
                iv.usePinned { iv ->
                    plainData.usePinned { plainData ->
                        encryptedBuffer.usePinned { encryptedBuffer ->
                            CCCrypt(
                                kCCEncrypt,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding,
                                key.addressOf(0),
                                kCCKeySizeAES256.toULong(),
                                iv.addressOf(0),
                                plainData.addressOf(0),
                                plainData.get().size.toULong(),
                                encryptedBuffer.addressOf(0),
                                encryptedBuffer.get().size.toULong(),
                                bytesCopied.ptr
                            )
                        }
                    }
                }
            }

            if (status != kCCSuccess) {
                throw CryptographyException("Failure while encrypting data using AES256")
            }

            outputSink.write(Buffer().write(iv), iv.size.toLong())
            outputSink.write(Buffer().write(encryptedBuffer), bytesCopied.value.toLong())
            bytesCopied.value.toLong()
        }
    } finally {
        assetDataSource.close()
        outputSink.close()
    }
}

actual fun decryptFileWithAES256(encryptedDataSource: Source, decryptedDataSink: Sink, secretKey: AES256Key): Long {
    try {
        val ivBuffer = Buffer()
        encryptedDataSource.read(ivBuffer, kCCBlockSizeAES128.toLong())
        val iv = ivBuffer.readByteArray()
        val encryptedData = encryptedDataSource.buffer().readByteArray()
        val decryptedBuffer = ByteArray(encryptedData.size + kCCBlockSizeAES128.toInt())

        // TODO avoid read whole file into memory by using streaming or block based API
        return memScoped {
            val bytesCopied = alloc<ULongVar>()
            val status = secretKey.data.usePinned { key ->
                iv.usePinned { iv ->
                    encryptedData.usePinned { encryptedData ->
                        decryptedBuffer.usePinned { decryptedBuffer ->
                            CCCrypt(
                                kCCDecrypt,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding,
                                key.addressOf(0),
                                kCCKeySizeAES256.toULong(),
                                iv.addressOf(0),
                                encryptedData.addressOf(0),
                                encryptedData.get().size.toULong(),
                                decryptedBuffer.addressOf(0),
                                decryptedBuffer.get().size.toULong(),
                                bytesCopied.ptr
                            )
                        }
                    }
                }
            }

            if (status != kCCSuccess) {
                throw CryptographyException("Failure while decrypting data using AES256")
            }

            decryptedDataSink.write(Buffer().write(decryptedBuffer), bytesCopied.value.toLong())
            bytesCopied.value.toLong()
        }
    } finally {
        encryptedDataSource.close()
        decryptedDataSink.close()
    }
}

actual fun generateRandomAES256Key(): AES256Key =
    AES256Key(generateRandomData(kCCKeySizeAES256.toInt()))

private fun generateRandomData(size: Int): ByteArray {
    val keyMaterial = ByteArray(size)
    val status = memScoped {
        keyMaterial.usePinned { keyMaterial ->
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), keyMaterial.addressOf(0))
        }
    }

    if (status != errSecSuccess) {
        throw CryptographyException("Failure while generating random data")
    }

    return keyMaterial
}
