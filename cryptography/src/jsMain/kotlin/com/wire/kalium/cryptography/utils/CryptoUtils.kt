package com.wire.kalium.cryptography.utils

import okio.Sink
import okio.Source

actual fun calcMd5(bytes: ByteArray): String {
    TODO("Not yet implemented")
}

actual fun calcSHA256(bytes: ByteArray): ByteArray {
    TODO("Not yet implemented")
}

actual fun calcFileMd5(dataSource: Source): String? {
    TODO("Not yet implemented")
}

actual fun calcFileSHA256(dataSource: Source): ByteArray? {
    TODO("Not yet implemented")
}

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData {
    TODO("Not yet implemented")
}

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData {
    TODO("Not yet implemented")
}

actual fun encryptFileWithAES256(assetDataSource: Source, key: AES256Key, outputSink: Sink): Long {
    TODO("Not yet implemented")
}

actual fun decryptFileWithAES256(encryptedDataSource: Source, decryptedDataSink: Sink, secretKey: AES256Key): Long =
    TODO("Not yet implemented")

actual fun generateRandomAES256Key(): AES256Key {
    TODO("Not yet implemented")
}
