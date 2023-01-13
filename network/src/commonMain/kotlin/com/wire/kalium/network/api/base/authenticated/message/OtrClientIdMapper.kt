package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.protobuf.otr.ClientId

internal class OtrClientIdMapper {
    fun toOtrClientId(clientId: String): ClientId = ClientId(clientId.decodeHexToLong())
}

private fun String.decodeHexToLong(): Long {

    @Suppress("MagicNumber")
    fun unsignedLong(mostSignificantBits: Long, leastSignificantBits: Long) =
        (mostSignificantBits shl 32) or leastSignificantBits

    val a = this.padStart(length = 16, '0').chunked(size = 8) {
        it.toString().toLongOrNull(radix = 16) ?: 0
    }

    return unsignedLong(a[0], a[1])
}
