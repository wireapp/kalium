package com.wire.kalium.cryptography.utils

import okio.Sink
import okio.Source

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData = AESEncrypt().encryptData(data, key)

actual fun encryptFileWithAES256(assetDataSource: Source, key: AES256Key, outputSink: Sink) =
    AESEncrypt().encryptFile(assetDataSource, key, outputSink)

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData = AESDecrypt(secretKey).decryptData(data)

actual fun decryptFileWithAES256(encryptedDataSource: Source, decryptedDataSink: Sink, secretKey: AES256Key) =
    AESDecrypt(secretKey).decryptFile(encryptedDataSource, decryptedDataSink)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
