package com.wire.kalium.cryptography.backup

import okio.Buffer

@OptIn(ExperimentalUnsignedTypes::class)
data class BackupHeader(
    val format: String,
    val version: String,
    val salt: UByteArray,
    val hashedUserId: UByteArray,
    val opslimit: Int,
    val memlimit: Int
) {

    private val extraGap = byteArrayOf(0x00)

    fun toByteArray(): ByteArray {
        val buffer = Buffer()
        buffer.write(format.encodeToByteArray())
        buffer.write(extraGap)
        buffer.write(version.encodeToByteArray())
        buffer.write(salt.toByteArray())
        buffer.write(hashedUserId.toByteArray())
        buffer.writeInt(opslimit)
        buffer.writeInt(memlimit)

        return buffer.readByteArray()
    }

    enum class HeaderDecodingErrors {
        INVALID_USER_ID, INVALID_VERSION, INVALID_FORMAT
    }
}
