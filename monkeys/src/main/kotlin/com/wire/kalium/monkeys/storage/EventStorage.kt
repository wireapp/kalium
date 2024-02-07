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

import com.wire.kalium.monkeys.MonkeyApplication
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.model.BackendConfig
import com.wire.kalium.monkeys.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel

abstract class EventStorage {
    protected abstract suspend fun store(event: Event)
    protected abstract fun CoroutineScope.readEvents(): ReceiveChannel<Event>
    abstract suspend fun readTeamConfig(): List<BackendConfig>
    abstract suspend fun storeBackends(backends: List<BackendConfig>)
    abstract suspend fun releaseResources()
    suspend fun processEvents(channel: ReceiveChannel<Event>) {
        while (MonkeyApplication.isActive.get()) {
            this.store(channel.receive())
        }
    }

    fun readProcessedEvents(scope: CoroutineScope) = scope.readEvents()
}

class DummyEventStorage : EventStorage() {
    override suspend fun store(event: Event) {
        logger.d("Processing $event")
    }

    override suspend fun storeBackends(backends: List<BackendConfig>) {
        logger.d("Storing backends: $backends")
    }

    override suspend fun releaseResources() {
        logger.d("Closing dummy storage")
    }

    override fun CoroutineScope.readEvents(): ReceiveChannel<Event> {
        throw NotImplementedError("This is not supported by the dummy storage")
    }

    override suspend fun readTeamConfig(): List<BackendConfig> {
        throw NotImplementedError("This is not supported by the dummy storage")
    }
}
