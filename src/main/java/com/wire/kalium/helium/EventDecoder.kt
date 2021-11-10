package com.wire.helium

import com.fasterxml.jackson.databind.ObjectMapper
import com.wire.xenon.tools.Logger
import com.wire.xenon.tools.Util
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.websocket.Decoder
import javax.websocket.EndpointConfig

class EventDecoder : Decoder.BinaryStream<com.wire.helium.models.Event?> {
    fun init(config: EndpointConfig?) {}
    fun destroy() {}
    fun decode(`is`: InputStream?): com.wire.helium.models.Event? {
        try {
            val str = String(Util.toByteArray(`is`), StandardCharsets.UTF_8)
            if (str.equals("pong", ignoreCase = true)) {
                Logger.debug("MessageDecoder: %s", str)
            } else {
                return mapper.readValue<com.wire.helium.models.Event>(str, com.wire.helium.models.Event::class.java)
            }
        } catch (e: IOException) {
            Logger.exception("MessageDecoder: %s", e, e.message)
        }
        return null
    }

    companion object {
        private val mapper = ObjectMapper()
    }
}