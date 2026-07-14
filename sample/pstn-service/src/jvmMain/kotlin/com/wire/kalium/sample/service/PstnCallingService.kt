@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
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

package com.wire.kalium.sample.service

import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.service.KaliumService
import com.wire.kalium.logic.service.KaliumServiceComponents
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.KaliumServiceRuntime
import com.wire.kalium.logic.service.api.ServiceConfig
import com.wire.kalium.logic.service.api.ServiceObserver
import com.wire.kalium.logic.service.api.ServiceResult

/**
 * Minimal PSTN-facing host that demonstrates lifecycle ownership without installing client
 * message persistence, full sync, call history, WorkManager, or backup behavior.
 */
@ExperimentalKaliumServiceApi
public class PstnCallingService private constructor(private val runtime: KaliumServiceRuntime) {
    public suspend fun start(): ServiceResult = runtime.start()

    public suspend fun join(conversationId: ConversationId): CallingResult = runtime.calls.join(conversationId)

    public suspend fun leave(conversationId: ConversationId): CallingResult = runtime.calls.leave(conversationId)

    public suspend fun close(): ServiceResult = runtime.close()

    public companion object {
        public fun <RawEvent, DecodedEvent, DecryptedEvent> create(
            config: ServiceConfig,
            components: KaliumServiceComponents<RawEvent, DecodedEvent, DecryptedEvent>,
            observer: ServiceObserver,
        ): PstnCallingService = PstnCallingService(KaliumService.create(config, components, observer))
    }
}
