@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.event.processing.ExperimentalEventProcessingApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.CallingEventHandler
import com.wire.kalium.calling.runtime.CallingRuntime
import com.wire.kalium.calling.runtime.InMemoryCallStateStore
import com.wire.kalium.conversation.runtime.ConversationRuntime
import com.wire.kalium.conversation.runtime.ProtocolRecordingConversationContextProvider
import com.wire.kalium.event.processing.EventProcessor
import com.wire.kalium.event.processing.EventRouter
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.KaliumServiceRuntime
import com.wire.kalium.logic.service.api.ServiceConfig
import com.wire.kalium.logic.service.api.ServiceObserver
import com.wire.kalium.logic.service.runtime.SharedKaliumServiceRuntime

/** JVM composition root. It intentionally installs no chat persistence, full sync, or call history. */
@ExperimentalKaliumServiceApi
public object KaliumService {
    public fun <RawEvent, DecodedEvent, DecryptedEvent> create(
        config: ServiceConfig,
        components: KaliumServiceComponents<RawEvent, DecodedEvent, DecryptedEvent>,
        observer: ServiceObserver,
    ): KaliumServiceRuntime {
        require(config.identity == components.identity) {
            "The service config and supplied components must belong to the same Wire identity"
        }
        val conversationRuntime = ConversationRuntime(
            ProtocolRecordingConversationContextProvider(
                components.conversationContextProvider,
                components.conversationProtocolStateStore,
            ),
        )
        val callingRuntime = CallingRuntime(
            contextProvider = conversationRuntime,
            transport = components.callTransport,
            conferenceMembership = components.conferenceMembership,
            engine = components.avsCallingEngine,
            selfConversationProvider = components.selfConversationProvider,
            controlHandler = components.callingControlHandler,
            stateStore = InMemoryCallStateStore(),
            eventSink = components.callEventSink,
            maxConcurrentCalls = config.maxConcurrentCalls,
        )
        val callingHandler = CallingEventHandler(
            extractor = components.callingPayloadExtractor,
            runtime = callingRuntime,
            idempotencyStore = components.callingEventIdempotencyStore,
        )
        val router = EventRouter(components.protocolEventHandlers.ordered + callingHandler)
        val processor = EventProcessor(
            components.eventSource,
            components.eventDeliveryStateStore,
            components.eventDecoder,
            components.eventDecryptor,
            router,
        )
        return SharedKaliumServiceRuntime(
            config = config,
            sessionManager = components.sessionManager,
            cryptoRuntime = components.cryptoRuntime,
            eventSource = components.eventSource,
            eventProcessor = processor,
            callingRuntime = callingRuntime,
            observer = observer,
        )
    }
}
