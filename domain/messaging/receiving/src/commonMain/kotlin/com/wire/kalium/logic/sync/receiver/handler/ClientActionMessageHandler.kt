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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun interface ClientActionMessageHandler {
    public suspend fun handle(message: Message.Signaling)
}

@InternalKaliumApi
public class ClientActionMessageHandlerImpl public constructor(
    private val persistMessage: PersistMessageUseCase,
) : ClientActionMessageHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(message: Message.Signaling) {
        logger.i(message = "ClientAction status update received: ")

        val systemMessage = Message.System(
            id = message.id,
            content = MessageContent.CryptoSessionReset,
            conversationId = message.conversationId,
            date = message.date,
            senderUserId = message.senderUserId,
            status = message.status,
            senderUserName = message.senderUserName,
            expirationData = null,
        )

        logger.i(message = "Persisting crypto session reset system message..")
        persistMessage(systemMessage)
    }
}
