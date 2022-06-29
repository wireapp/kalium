package com.wire.kalium.cryptography.utils

import okio.FileSystem
import okio.Path
import okio.Source

actual fun calcMd5(bytes: ByteArray): String {
    TODO("Not yet implemented")
}

actual fun calcSHA256(bytes: ByteArray): ByteArray {
    TODO("Not yet implemented")
}

actual fun calcFileMd5(dataPath: Path, kaliumFileSystem: FileSystem): String? {
    TODO("Not yet implemented")
}

actual fun calcFileSHA256(dataPath: Path, kaliumFileSystem: FileSystem): ByteArray? {
    TODO("Not yet implemented")
}

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData {
    TODO("Not yet implemented")
}

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData {
    TODO("Not yet implemented")
}

actual fun encryptFileWithAES256(rawDataPath: Path, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem): Long {
    TODO("Not yet implemented")
}

actual fun decryptFileWithAES256(
    encryptedDataSource: Source,
    decryptedDataPath: Path,
    secretKey: AES256Key,
    kaliumFileSystem: FileSystem
): Long {
    TODO("Not yet implemented")
}

actual fun generateRandomAES256Key(): AES256Key {
    TODO("Not yet implemented")
}
