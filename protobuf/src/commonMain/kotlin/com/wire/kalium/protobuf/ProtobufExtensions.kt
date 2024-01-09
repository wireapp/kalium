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

package com.wire.kalium.protobuf

import pbandk.Message
import pbandk.decodeFromByteArray
import pbandk.encodeToByteArray

/**
 * Encode this message to a ByteArray using the protocol buffer binary encoding.
 * This function is a wrapper to PBandK's encoding function.
 */
public fun <T : Message> T.encodeToByteArray(): ByteArray =
    encodeToByteArray()

/**
 * Decode a binary protocol buffer message from [arr].
 * This function is a wrapper to PBandK's decoding function.
 */
public fun <T : Message> Message.Companion<T>.decodeFromByteArray(arr: ByteArray): T =
    decodeFromByteArray(arr)
