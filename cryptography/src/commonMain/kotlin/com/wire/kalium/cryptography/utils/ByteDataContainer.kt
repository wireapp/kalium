package com.wire.kalium.cryptography.utils

/**
 * Simple utility class that enables holding ByteArrays.
 * This implements equals and hash code.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class ByteDataContainer(val data: ByteArray) {

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ByteDataContainer

        if (!data.contentEquals(other.data)) return false

        return true
    }
}

class SHA256Key(data: ByteArray) : ByteDataContainer(data)

class AES256Key(data: ByteArray) : ByteDataContainer(data)

class EncryptedData(data: ByteArray) : ByteDataContainer(data)

class PlainData(data: ByteArray) : ByteDataContainer(data)
