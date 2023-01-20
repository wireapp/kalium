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
    encryptFileWithAES256(Buffer().write(data.data), key, outputBuffer)
    return EncryptedData(outputBuffer.readByteArray())
}

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData {
    val outputBuffer = Buffer()
    decryptFileWithAES256(Buffer().write(data.data), outputBuffer, secretKey)
    return PlainData(outputBuffer.readByteArray())
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
