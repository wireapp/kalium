package com.wire.kalium.network.api.message

import com.google.protobuf.ByteString
import com.wire.messages.Otr
import java.nio.charset.Charset

class OtrClientEntryMapper {

    private val clientIdMapper = OtrClientIdMapper()

    fun toOtrClientEntry(clientPayload: Map.Entry<String, String>): Otr.ClientEntry = Otr.ClientEntry.newBuilder()
        .setClient(clientIdMapper.toOtrClientId(clientPayload.key))
        .setText(ByteString.copyFrom(clientPayload.value, Charset.defaultCharset()))
        .build()
}
