/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.utils.calcSHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.ceil
import kotlin.math.min

internal interface BackupEncryptionKeyDeriver {
    fun deriveBase64Passphrase(backupRootKey: ByteArray, backupId: String): String
}

@OptIn(ExperimentalEncodingApi::class)
internal object HkdfBackupEncryptionKeyDeriver : BackupEncryptionKeyDeriver {
    private const val OUTPUT_KEY_LENGTH = 32
    private const val HASH_LENGTH = 32
    private const val HMAC_BLOCK_SIZE = 64
    private const val HKDF_INFO = "wire-mp-backup-content-v1"
    const val ENCRYPTION_ALGORITHM = "backup-root-key-hkdf-v1"

    override fun deriveBase64Passphrase(backupRootKey: ByteArray, backupId: String): String =
        Base64.encode(
            hkdfSha256(
                inputKeyMaterial = backupRootKey,
                salt = backupId.encodeToByteArray(),
                info = HKDF_INFO.encodeToByteArray(),
                outputLength = OUTPUT_KEY_LENGTH,
            )
        )

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        require(outputLength > 0) { "Output length must be positive" }
        require(outputLength <= HASH_LENGTH * 255) { "Output length is too large for HKDF-SHA256" }

        val actualSalt = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        val pseudoRandomKey = hmacSha256(actualSalt, inputKeyMaterial)
        val output = ByteArray(outputLength)
        var previousBlock = ByteArray(0)
        var outputOffset = 0
        val blockCount = ceil(outputLength.toDouble() / HASH_LENGTH).toInt()

        for (blockIndex in 1..blockCount) {
            previousBlock = hmacSha256(
                key = pseudoRandomKey,
                data = previousBlock + info + blockIndex.toByte(),
            )
            val bytesToCopy = min(previousBlock.size, outputLength - outputOffset)
            previousBlock.copyInto(output, destinationOffset = outputOffset, endIndex = bytesToCopy)
            outputOffset += bytesToCopy
        }

        return output
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val normalizedKey = if (key.size > HMAC_BLOCK_SIZE) calcSHA256(key) else key
        val keyBlock = ByteArray(HMAC_BLOCK_SIZE)
        normalizedKey.copyInto(keyBlock)

        val outerKeyPad = ByteArray(HMAC_BLOCK_SIZE) { index -> (keyBlock[index].toInt() xor 0x5c).toByte() }
        val innerKeyPad = ByteArray(HMAC_BLOCK_SIZE) { index -> (keyBlock[index].toInt() xor 0x36).toByte() }

        return calcSHA256(outerKeyPad + calcSHA256(innerKeyPad + data))
    }
}
