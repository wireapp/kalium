/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation.mls

import okio.Buffer
import kotlin.jvm.JvmInline

/**
 * Represents an MLS GroupInfo structure containing group state information.
 *
 * @property value The raw bytes of the TLS-encoded GroupInfo structure
 */
@JvmInline
value class GroupInfo(val value: ByteArray) {

    /**
     * Extracts the epoch value from the GroupInfo TLS-encoded structure.
     *
     * According to RFC 9420, an epoch represents a state of a group in which a specific set
     * of authenticated clients hold shared cryptographic state. Each epoch has a distinct
     * ratchet tree and secret tree, and epochs progress linearly in sequence.
     *
     * The GroupInfo structure begins with a GroupContext containing:
     * - version (2 bytes)
     * - cipher_suite (2 bytes)
     * - group_id (variable length, prefixed with MLS varint)
     * - epoch (8 bytes, uint64 in big-endian format)
     *
     * @return The epoch value as a Long, or null if parsing fails due to malformed data
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc9420#name-group-context">RFC 9420 - Group Context</a>
     */
    fun extractEpoch(): Long? {
        return try {
            val buffer = Buffer().write(value)

            // GroupInfo starts with GroupContext:
            // version(2) | cipher_suite(2) | group_id<V> | epoch(8) | ...

            // Skip version (2 bytes) and cipher_suite (2 bytes)
            buffer.skip(4)

            // Read group_id length using MLS varint
            val groupIdLen = buffer.readMlsVarInt()

            // Skip group_id
            buffer.skip(groupIdLen.toLong())

            // Read epoch (uint64, big-endian)
            buffer.readLong()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Reads an MLS variable-length integer from an Okio Buffer (similar to QUIC varint encoding).
 * - Prefix 00 (bits 7-6): 1 byte total, value in bits 5-0
 * - Prefix 01 (bits 7-6): 2 bytes total, value in bits 5-0 of first byte + 8 bits of second byte
 * - Prefix 10 (bits 7-6): 4 bytes total, value in bits 5-0 of first byte + 24 bits from remaining bytes
 * - Prefix 11 (bits 7-6): Invalid
 * Minimal encoding is required (throws IllegalArgumentException if not minimal)
 */
private fun Buffer.readMlsVarInt(): Int {
    val b0 = readByte().toInt() and 0xFF
    val prefix = b0 ushr 6

    return when (prefix) {
        0 -> b0 and 0x3F
        1 -> {
            val b1 = readByte().toInt() and 0xFF
            val v = ((b0 and 0x3F) shl 8) or b1
            if (v < 64) throw IllegalArgumentException("Non-minimal varint")
            v
        }
        2 -> {
            val v = ((b0 and 0x3F) shl 24) or
                    ((readByte().toInt() and 0xFF) shl 16) or
                    ((readByte().toInt() and 0xFF) shl 8) or
                    (readByte().toInt() and 0xFF)
            if (v < 16384) throw IllegalArgumentException("Non-minimal varint")
            v
        }
        else -> throw IllegalArgumentException("Invalid MLS varint (prefix 11)")
    }
}
