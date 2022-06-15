package com.wire.kalium.cryptography.utils

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

@Throws(IOException::class)
internal fun calculateFileChecksum(digest: MessageDigest, file: File): ByteArray {
    // Get file input stream for reading the file content
    val fis = FileInputStream(file)

    // Create byte array to read data in chunks
    val byteArray = ByteArray(8 * 1024)
    var bytesCount: Int

    // Read file data and update in message digest
    while (fis.read(byteArray).also { bytesCount = it } != -1) {
        digest.update(byteArray, 0, bytesCount)
    }

    // Close the stream, we don't need it now
    fis.close()

    // Return the hashed bytes
    return digest.digest()
}
