package com.wire.kalium.state

import kotlin.Throws
import java.io.IOException
import com.wire.kalium.backend.models.NewBot
import java.util.UUID
import com.fasterxml.jackson.databind.ObjectMapper
import com.wire.kalium.exceptions.MissingStateException
import java.io.File

class FileState(private val path: String?, private val botId: UUID?) : State {
    @Throws(IOException::class)
    override fun saveState(newBot: NewBot?): Boolean {
        val file = getStateFile()
        val mapper = ObjectMapper()
        mapper.writeValue(file, newBot)
        return true
    }

    @Throws(IOException::class)
    override fun getState(): NewBot? {
        val file = getStateFile()
        if (!file.exists()) throw MissingStateException(botId)
        val mapper = ObjectMapper()
        return mapper.readValue(file, NewBot::class.java)
    }

    override fun removeState(): Boolean {
        val file = getStateFile()
        return file.delete()
    }

    fun hasState(): Boolean {
        val stateFile = getStateFile()
        return stateFile.exists()
    }

    fun hasFile(filename: String?): Boolean {
        val stateFile = getFile(filename)
        return stateFile.exists()
    }

    private fun getStateFile(): File? {
        return getFile(STATE_FILENAME)
    }

    private fun getFile(filename: String?): File? {
        return File(String.format("%s/%s/%s", path, botId, filename))
    }

    companion object {
        private val STATE_FILENAME: String? = "state.json"
    }

    init {
        val dir = File(String.format("%s/%s", path, botId))
        dir.mkdirs()
    }
}
