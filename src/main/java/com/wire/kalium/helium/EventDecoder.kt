package com.wire.kalium.helium

import com.fasterxml.jackson.databind.ObjectMapper
import com.wire.kalium.helium.models.Event
import com.wire.kalium.tools.Logger
import com.wire.kalium.tools.Util
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
                return mapper.readValue<Event>(str, Event::class.java)
            }
        } catch (e: IOException) {
            Logger.exception(message = "MessageDecoder: ${e.message}", throwable = e)
        }
        return null
    }

    companion object {
        private val mapper = ObjectMapper()
    }
}
