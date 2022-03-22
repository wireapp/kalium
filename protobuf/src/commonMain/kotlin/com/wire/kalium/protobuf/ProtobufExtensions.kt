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
