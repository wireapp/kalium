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
import platform.CoreCrypto.CCCryptorCreate
import platform.CoreCrypto.CCCryptorFinal
import platform.CoreCrypto.CCCryptorRefVar
import platform.CoreCrypto.CCCryptorRelease
import platform.CoreCrypto.CCCryptorUpdate
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

@Suppress("LongMethod", "ThrowsCount")
actual fun encryptFileWithAES256(source: Source, key: AES256Key, sink: Sink): Long {
    val iv = generateRandomData(kCCBlockSizeAES128.toInt())
    val encryptedBuffer = ByteArray(BUFFER_SIZE + kCCBlockSizeAES128.toInt())

    return memScoped {
        val cryptor = alloc<CCCryptorRefVar>()
        val bytesCopied = alloc<ULongVar>()
        var bytesCopiedTotal: ULong = 0u

        try {
            key.data.usePinned { key ->
                iv.usePinned { iv ->
                    val status = CCCryptorCreate(
                        kCCEncrypt,
                        kCCAlgorithmAES,
                        kCCOptionPKCS7Padding,
                        key.addressOf(0),
                        kCCKeySizeAES256.toULong(),
                        iv.addressOf(0),
                        cryptor.ptr
                    )

                    if (status != kCCSuccess) {
                        throw CryptographyException("Failure on CCCryptorCreate, status = $status")
                    }
                }
            }

            Buffer().use {
                sink.write(it.write(iv), iv.size.toLong())
            }

            val inputBuffer = source.buffer()
            val unencryptedBuffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (inputBuffer.read(unencryptedBuffer, 0, BUFFER_SIZE).also { bytesRead = it } > 0) {
                unencryptedBuffer.usePinned { unencryptedBuffer ->
                    encryptedBuffer.usePinned { encryptedBuffer ->
                        val status = CCCryptorUpdate(
                            cryptor.value,
                            unencryptedBuffer.addressOf(0),
                            bytesRead.toULong(),
                            encryptedBuffer.addressOf(0),
                            encryptedBuffer.get().size.toULong(),
                            bytesCopied.ptr
                        )

                        if (status != kCCSuccess) {
                            throw CryptographyException("Failure on CCCryptorUpdate, status = $status")
                        }
                    }
                }

                Buffer().use {
                    sink.write(it.write(encryptedBuffer), bytesCopied.value.toLong())
                }
                bytesCopiedTotal += bytesCopied.value
            }

            encryptedBuffer.usePinned { encryptedBuffer ->
                val status = CCCryptorFinal(
                    cryptor.value,
                    encryptedBuffer.addressOf(0),
                    encryptedBuffer.get().size.toULong(),
                    bytesCopied.ptr
                )

                if (status != kCCSuccess) {
                    throw CryptographyException("Failure on CCCryptorFinal, status = $status")
                }
            }

            Buffer().use {
                sink.write(it.write(encryptedBuffer), bytesCopied.value.toLong())
            }
            bytesCopiedTotal += bytesCopied.value
        } finally {
            CCCryptorRelease(cryptor.value)
            source.close()
            sink.close()
        }

        bytesCopiedTotal.toLong()
    }
}

@Suppress("LongMethod", "ThrowsCount")
actual fun decryptFileWithAES256(source: Source, sink: Sink, secretKey: AES256Key): Long {
    val decryptedBuffer = ByteArray(BUFFER_SIZE + kCCBlockSizeAES128.toInt())

    return memScoped {
        val cryptor = alloc<CCCryptorRefVar>()
        val bytesCopied = alloc<ULongVar>()
        var bytesCopiedTotal: ULong = 0u

        try {
            val iv = Buffer().use {
                source.read(it, kCCBlockSizeAES128.toLong())
                it.readByteArray()
            }

            secretKey.data.usePinned { key ->
                iv.usePinned { iv ->
                    val status = CCCryptorCreate(
                        kCCDecrypt,
                        kCCAlgorithmAES,
                        kCCOptionPKCS7Padding,
                        key.addressOf(0),
                        kCCKeySizeAES256.toULong(),
                        iv.addressOf(0),
                        cryptor.ptr
                    )

                    if (status != kCCSuccess) {
                        throw CryptographyException("Failure on CCCryptorCreate, status = $status")
                    }
                }
            }

            val inputBuffer = source.buffer()
            val encryptedBuffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (inputBuffer.read(encryptedBuffer, 0, BUFFER_SIZE).also { bytesRead = it } > 0) {
                decryptedBuffer.usePinned { decryptedBuffer ->
                    encryptedBuffer.usePinned { encryptedDataBlock ->
                        val status = CCCryptorUpdate(
                            cryptor.value,
                            encryptedDataBlock.addressOf(0),
                            bytesRead.toULong(),
                            decryptedBuffer.addressOf(0),
                            decryptedBuffer.get().size.toULong(),
                            bytesCopied.ptr
                        )

                        if (status != kCCSuccess) {
                            throw CryptographyException("Failure on CCCryptorUpdate, status = $status")
                        }
                    }
                }

                Buffer().use {
                    sink.write(it.write(decryptedBuffer), bytesCopied.value.toLong())
                }
                bytesCopiedTotal += bytesCopied.value
            }

            decryptedBuffer.usePinned { decryptedBuffer ->
                val status = CCCryptorFinal(
                    cryptor.value,
                    decryptedBuffer.addressOf(0),
                    decryptedBuffer.get().size.toULong(),
                    bytesCopied.ptr
                )

                if (status != kCCSuccess) {
                    throw CryptographyException("Failure on CCCryptorFinal, status = $status")
                }
            }

            Buffer().use {
                sink.write(it.write(decryptedBuffer), bytesCopied.value.toLong())
            }
            bytesCopiedTotal += bytesCopied.value
        } finally {
            CCCryptorRelease(cryptor.value)
            source.close()
            sink.close()
        }

        bytesCopiedTotal.toLong()
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

private const val BUFFER_SIZE = 1024 * 8
