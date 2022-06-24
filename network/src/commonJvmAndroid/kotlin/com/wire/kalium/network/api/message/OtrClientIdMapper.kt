package com.wire.kalium.network.api.message

import com.wire.kalium.protobuf.otr.ClientId
import java.math.BigInteger

class OtrClientIdMapper {

    fun toOtrClientId(clientId: String): ClientId = ClientId(BigInteger(clientId, CLIENT_ID_RADIX).toLong())

    fun fromOtrClientId(otrClientId: ClientId): String = otrClientId.client.toBigInteger().toString(CLIENT_ID_RADIX)

    companion object {
        private const val CLIENT_ID_RADIX = 16
    }
}
