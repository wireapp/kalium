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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger

internal interface DataTransferEventHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.DataTransfer
    )
}

internal class DataTransferEventHandlerImpl(
    private val selfUserId: UserId,
    private val userConfigRepository: UserConfigRepository
) : DataTransferEventHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.DataTransfer
    ) {
        // DataTransfer from another user or null tracking identifier shouldn't happen,
        // If it happens, it's unnecessary, and we can squish some performance by skipping it completely
        if (message.senderUserId != selfUserId || messageContent.trackingIdentifier == null) return

        val currentTrackingIdentifier = userConfigRepository.getTrackingIdentifier()
        currentTrackingIdentifier?.let {
            if (currentTrackingIdentifier != messageContent.trackingIdentifier!!.identifier) {
                userConfigRepository.setPreviousTrackingIdentifier(identifier = currentTrackingIdentifier)
                kaliumLogger.d("$TAG Moved Current Tracking Identifier to Previous")
            }
        }

        messageContent.trackingIdentifier?.let { trackingIdentifier ->
            userConfigRepository.setTrackingIdentifier(
                identifier = trackingIdentifier.identifier
            )
            kaliumLogger.d("$TAG Tracking Identifier Updated")
        }
    }

    private companion object {
        const val TAG = "DataTransferEventHandler"
    }
}
