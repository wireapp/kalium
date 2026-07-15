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

import com.wire.kalium.calling.runtime.AvsCallingEngine
import com.wire.kalium.calling.runtime.CallEventSink
import com.wire.kalium.calling.runtime.CallTransport
import com.wire.kalium.calling.runtime.CallingControlHandler
import com.wire.kalium.calling.runtime.CallingEventIdempotencyStore
import com.wire.kalium.calling.runtime.CallingPayloadExtractor
import com.wire.kalium.calling.runtime.ConferenceMembership
import com.wire.kalium.calling.runtime.ServiceSelfConversationProvider
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationProtocolStateStore
import com.wire.kalium.event.processing.EventDecoder
import com.wire.kalium.event.processing.EventDecryptor
import com.wire.kalium.event.processing.EventHandler
import com.wire.kalium.event.processing.EventHandlerRequirement
import com.wire.kalium.events.EventDeliveryStateStore
import com.wire.kalium.events.EventSource
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceCryptoRuntime
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.logic.service.api.ServiceSessionManager

/**
 * Required protocol handlers for all call protocols retained by the service composition.
 *
 * A unified handler may be used only when it durably processes both Proteus and MLS protocol
 * state. Otherwise callers must supply one handler for each protocol. The calling handler is
 * installed separately and always runs after these handlers.
 */
@ExperimentalKaliumServiceApi
public class RequiredProtocolEventHandlers<Event> private constructor(
    internal val ordered: List<EventHandler<Event>>,
) {
    init {
        require(ordered.all { it.requirement == EventHandlerRequirement.REQUIRED }) {
            "Every installed protocol event handler must declare REQUIRED processing"
        }
    }

    public companion object {
        public fun <Event> forProteusAndMls(
            proteus: EventHandler<Event>,
            mls: EventHandler<Event>,
            additional: List<EventHandler<Event>> = emptyList(),
        ): RequiredProtocolEventHandlers<Event> = RequiredProtocolEventHandlers(listOf(proteus, mls) + additional)

        public fun <Event> unified(
            handler: EventHandler<Event>,
            additional: List<EventHandler<Event>> = emptyList(),
        ): RequiredProtocolEventHandlers<Event> = RequiredProtocolEventHandlers(listOf(handler) + additional)
    }
}

/**
 * One identity-scoped set of supplied production components.
 *
 * Every component, including both durable stores, must be constructed for [identity]. Grouping
 * them makes that ownership explicit and lets [KaliumService] reject a config/bundle mismatch.
 * Implementations must not use global provider caches; service deployments use LOCAL cache scope.
 */
@ExperimentalKaliumServiceApi
@Suppress("LongParameterList")
public class KaliumServiceComponents<RawEvent, DecodedEvent, DecryptedEvent>(
    public val identity: ServiceIdentity,
    public val sessionManager: ServiceSessionManager,
    public val cryptoRuntime: ServiceCryptoRuntime,
    public val eventSource: EventSource<RawEvent>,
    public val eventDeliveryStateStore: EventDeliveryStateStore,
    public val eventDecoder: EventDecoder<RawEvent, DecodedEvent>,
    public val eventDecryptor: EventDecryptor<DecodedEvent, DecryptedEvent>,
    public val protocolEventHandlers: RequiredProtocolEventHandlers<DecryptedEvent>,
    public val callingPayloadExtractor: CallingPayloadExtractor<DecryptedEvent>,
    public val callingEventIdempotencyStore: CallingEventIdempotencyStore,
    public val conversationContextProvider: ConversationContextProvider,
    public val conversationProtocolStateStore: ConversationProtocolStateStore,
    public val callTransport: CallTransport,
    public val conferenceMembership: ConferenceMembership,
    public val avsCallingEngine: AvsCallingEngine,
    public val selfConversationProvider: ServiceSelfConversationProvider,
    public val callingControlHandler: CallingControlHandler,
    public val callEventSink: CallEventSink,
)
