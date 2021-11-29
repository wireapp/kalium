package com.wire.kalium.helium

import com.wire.kalium.models.backend.Event
import com.wire.kalium.tools.KtxSerializer
import com.wire.kalium.tools.Logger
import java.io.IOException
import java.io.InputStream
import javax.websocket.Decoder
import javax.websocket.EndpointConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

class EventDecoder : Decoder.BinaryStream<Event> {
    override fun init(config: EndpointConfig?) {}
    override fun destroy() {}

    @ExperimentalSerializationApi
    override fun decode(stream: InputStream): Event? {
        try {
            return KtxSerializer.json.decodeFromStream(stream)
        } catch (e: IOException) {
            Logger.exception(message = "MessageDecoder: ${e.message}", throwable = e)
        }
        return null
    }
}
