package com.wire.kalium.state

import kotlin.Throws
import java.io.IOException
import com.wire.kalium.backend.models.NewBot
import java.util.UUID
import com.fasterxml.jackson.databind.ObjectMapper
import com.wire.kalium.exceptions.MissingStateException
import org.jdbi.v3.core.Jdbi

class JdbiState(private val botId: UUID?, jdbi: Jdbi?) : State {
    private val statesDAO: StatesDAO?
    @Throws(IOException::class)
    override fun saveState(newBot: NewBot?): Boolean {
        val str = mapper.writeValueAsString(newBot)
        return 1 == statesDAO.insert(botId, str)
    }

    @Throws(IOException::class)
    override fun getState(): NewBot? {
        val str = statesDAO.get(botId) ?: throw MissingStateException(botId)
        return mapper.readValue(str, NewBot::class.java)
    }

    override fun removeState(): Boolean {
        return 1 == statesDAO.delete(botId)
    }

    companion object {
        private val mapper: ObjectMapper? = ObjectMapper()
    }

    init {
        statesDAO = jdbi.onDemand(StatesDAO::class.java)
    }
}
