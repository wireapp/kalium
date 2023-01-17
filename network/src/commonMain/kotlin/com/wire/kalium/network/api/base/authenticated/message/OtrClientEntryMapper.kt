package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.protobuf.otr.ClientEntry
import pbandk.ByteArr

internal class OtrClientEntryMapper {

    private val clientIdMapper = OtrClientIdMapper()
    fun toOtrClientEntry(clientPayload: Map.Entry<String, ByteArray>): ClientEntry = ClientEntry(
        client = clientIdMapper.toOtrClientId(clientPayload.key),
        text = ByteArr(clientPayload.value),
    )
}
