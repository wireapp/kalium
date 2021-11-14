package com.wire.kalium.helium

import com.wire.kalium.models.backend.Event
import com.wire.kalium.tools.KtxSerializer
import com.wire.kalium.tools.Logger
import com.wire.kalium.tools.Util
import kotlinx.serialization.decodeFromString
import java.io.IOException
import java.io.InputStream
import javax.websocket.Decoder
import javax.websocket.EndpointConfig

class EventDecoder : Decoder.BinaryStream<Event?> {
    override fun init(config: EndpointConfig?) {}
    override fun destroy() {}
    override fun decode(`is`: InputStream?): Event? {
        try {
            //val str = String(Util.toByteArray(`is`).toString(), StandardCharsets.UTF_8)
            val str = Util.toByteArray(`is`).toString()
            if (str.equals("pong", ignoreCase = true)) {
                Logger.debug("MessageDecoder: %s", str)
            } else {
                return KtxSerializer.json.decodeFromString<Event>(str)
            }
        } catch (e: IOException) {
            Logger.exception(message = "MessageDecoder: ${e.message}", throwable = e)
        }
        return null
    }
}
