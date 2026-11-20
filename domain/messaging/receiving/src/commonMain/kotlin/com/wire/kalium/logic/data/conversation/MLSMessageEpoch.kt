/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import okio.Buffer

/**
 * After an external join, CoreCrypto reports a message from the replaced epoch as this generic failure,
 * not MessageEpochTooOld. A past epoch's message it cannot decrypt now never becomes decryptable.
 * Only this exact failure: CoreCrypto also reports storage errors as Other, and those may pass on retry.
 */
inline fun isPastEpochDecryptFailure(failure: CoreFailure, message: ByteArray, localEpoch: () -> ULong?): Boolean =
    failure == MLSFailure.Other(CORE_CRYPTO_DECRYPT_FAILURE) &&
        mlsMessageEpoch(message)?.let { messageEpoch -> localEpoch()?.let { messageEpoch < it } } == true

const val CORE_CRYPTO_DECRYPT_FAILURE = "An application message failed to decrypt"

/** The epoch in the plaintext header of an RFC 9420 public or private message, or null for anything else. */
fun mlsMessageEpoch(message: ByteArray): ULong? = runCatching {
    val buffer = Buffer().write(message)
    // MLSMessage: version (mls10) and wire format (public or private), then group_id<V> and the epoch.
    check(buffer.readShort().toInt() == MLS_10 && buffer.readShort().toInt() in PUBLIC_MESSAGE..PRIVATE_MESSAGE)
    val first = buffer.readByte().toInt() and BYTE_MASK
    // A <V> length is 1, 2 or 4 bytes; the top two bits of its first byte give the size.
    val lengthBytes = 1 shl (first ushr VARINT_SIZE_SHIFT)
    check(lengthBytes <= MAX_VARINT_BYTES)
    var groupIdLength = (first and VARINT_VALUE_MASK).toLong()
    repeat(lengthBytes - 1) {
        groupIdLength = (groupIdLength shl Byte.SIZE_BITS) or (buffer.readByte().toLong() and BYTE_MASK.toLong())
    }
    buffer.skip(groupIdLength)
    buffer.readLong().toULong()
}.getOrNull()

private const val MLS_10 = 1
private const val PUBLIC_MESSAGE = 1
private const val PRIVATE_MESSAGE = 2
private const val BYTE_MASK = 0xFF
private const val VARINT_SIZE_SHIFT = 6
private const val VARINT_VALUE_MASK = 0x3F
private const val MAX_VARINT_BYTES = 4
