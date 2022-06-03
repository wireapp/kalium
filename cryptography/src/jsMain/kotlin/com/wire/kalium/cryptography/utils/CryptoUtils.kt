package com.wire.kalium.cryptography.utils

import okio.FileSystem
import okio.Path

actual fun calcMd5(bytes: ByteArray): String {
    TODO("Not yet implemented")
}

actual fun calcSHA256(bytes: ByteArray): ByteArray {
    TODO("Not yet implemented")
}

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem): Boolean {
    TODO("Not yet implemented")
}

actual fun decryptDataWithAES256(
    encryptedDataPath: Path,
    decryptedDataPath: Path,
    secretKey: AES256Key,
    kaliumFileSystem: FileSystem
): Boolean {
    TODO("Not yet implemented")
}

actual fun generateRandomAES256Key(): AES256Key {
    TODO("Not yet implemented")
}
