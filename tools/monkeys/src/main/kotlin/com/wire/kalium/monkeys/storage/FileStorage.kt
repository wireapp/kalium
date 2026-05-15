/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.monkeys.storage

import com.wire.kalium.monkeys.model.BackendConfig
import com.wire.kalium.monkeys.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.FileOutputStream
import com.wire.kalium.monkeys.model.EventStorage.FileStorage as EventConfig

class FileStorage(val config: EventConfig) : EventStorage() {
    private val writer: FileOutputStream by lazy { File(config.eventsLocation).outputStream() }
    private val reader = lazy { File(config.eventsLocation).bufferedReader() }
    private val teamsReader = lazy { File(config.teamsLocation).inputStream() }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun store(event: Event) {
        Json.encodeToStream(event, this.writer)
        withContext(Dispatchers.IO) {
            writer.write(System.lineSeparator().toByteArray())
            writer.flush()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun storeBackends(backends: List<BackendConfig>) {
        File(config.teamsLocation).outputStream().use {
            Json.encodeToStream(backends, it)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun CoroutineScope.readEvents() = produce<Event> {
        for (event in reader.value.lines().map { Json.decodeFromString<Event>(it) }) {
            send(event)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readTeamConfig(): List<BackendConfig> {
        return Json.decodeFromStream(this.teamsReader.value)
    }

    override suspend fun releaseResources() {
        withContext(Dispatchers.IO) {
            writer.close()
            if (reader.isInitialized()) {
                reader.value.close()
            }
            if (teamsReader.isInitialized()) {
                teamsReader.value.close()
            }
        }
    }
}
