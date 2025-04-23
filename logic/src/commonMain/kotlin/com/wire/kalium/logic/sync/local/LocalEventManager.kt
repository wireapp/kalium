/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.local

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * LocalEventManager listens for local events from LocalEventRepository and processes them using EventProcessor.
 */
internal interface LocalEventManager {
    /**
     * Starts processing local events.
     */
    fun startProcessing()
}

internal class LocalEventManagerImpl(
    private val localEventRepository: LocalEventRepository,
    private val eventProcessor: EventProcessor,
    scope: CoroutineScope,
    dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : LocalEventManager, CoroutineScope {

    override val coroutineContext = scope.coroutineContext + dispatchers.io

    override fun startProcessing() {
        launch {
            localEventRepository.observeLocalEvents().collect { eventEnvelope ->
                processEvent(eventEnvelope)
            }
        }
    }

    private suspend fun processEvent(eventEnvelope: EventEnvelope) {
        when (val result = eventProcessor.processEvent(eventEnvelope)) {
            is Either.Right -> {
                kaliumLogger.i("Event processed successfully: ${eventEnvelope.event.id}", tag = LOCAL_EVENT)
            }

            is Either.Left -> {
                kaliumLogger.w(
                    "Failed to process event: ${eventEnvelope.event.id}, error: ${result.value}",
                    tag = LOCAL_EVENT
                )
            }
        }
    }

    companion object {
        private const val LOCAL_EVENT = "LocalEvent"
    }
}
