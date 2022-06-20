package com.wire.kalium.cryptography.utils

import okio.FileSystem
import okio.Path
import okio.Source

actual fun calcMd5(dataPath: Path, kaliumFileSystem: FileSystem): String? {
    TODO("Not yet implemented")
}

actual fun calcSHA256(dataPath: Path, kaliumFileSystem: FileSystem): ByteArray? {
    TODO("Not yet implemented")
}

actual fun encryptDataWithAES256(unencryptedDataPath: Path, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem): Long {
    TODO("Not yet implemented")
}

actual fun decryptDataWithAES256(
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
