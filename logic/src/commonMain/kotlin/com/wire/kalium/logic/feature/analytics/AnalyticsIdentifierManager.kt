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
package com.wire.kalium.logic.feature.analytics

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.datetime.Clock

interface AnalyticsIdentifierManager {

    /**
     * When doing a migration of tracking identifier (receive new identifier -> migrate new identifier),
     * we should then after migration is complete, delete the previous tracking identifier.
     *
     * Previous tracking identifier is kept because in case migration or network failure, we still have both values
     * to do the correct migration of tracking identifiers.
     */
    suspend fun onMigrationComplete()

    /**
     * When user first login, we generate a new tracking identifier, when this tracking identifier is set,
     * we need to send a message to the other clients of the user, to ensure they also use this newly generated identifier.
     */
    suspend fun propagateTrackingIdentifier(identifier: String)
}

@Suppress("FunctionNaming", "LongParameterList")
internal fun AnalyticsIdentifierManager(
    messageSender: MessageSender,
    userConfigRepository: UserConfigRepository,
    selfUserId: UserId,
    selfClientIdProvider: CurrentClientIdProvider,
    selfConversationIdProvider: SelfConversationIdProvider,
    syncManager: SyncManager,
    defaultLogger: KaliumLogger = kaliumLogger
) = object : AnalyticsIdentifierManager {

    private val TAG = "AnalyticsIdentifierManager"
    private val logger = defaultLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.ANALYTICS)

    override suspend fun onMigrationComplete() {
        userConfigRepository.deletePreviousTrackingIdentifier()

        logger.i("$TAG Previous Tracking Identifier deleted.")
    }

    override suspend fun propagateTrackingIdentifier(identifier: String) {
        syncManager.waitUntilLive()

        val messageContent = MessageContent.DataTransfer(
            trackingIdentifier = MessageContent.DataTransfer.TrackingIdentifier(
                identifier = identifier
            )
        )
        selfClientIdProvider().flatMap { currentClientId ->
            selfConversationIdProvider().flatMap { selfConversationIdList ->
                selfConversationIdList.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                    val date = Clock.System.now()
                    val message = Message.Signaling(
                        id = uuid4().toString(),
                        content = messageContent,
                        conversationId = selfConversationId,
                        date = date,
                        senderUserId = selfUserId,
                        senderClientId = currentClientId,
                        status = Message.Status.Sent,
                        isSelfMessage = true,
                        expirationData = null
                    )

                    messageSender.sendMessage(
                        message = message,
                        messageTarget = MessageTarget.Conversation()
                    ).also {
                        logger.i("$TAG Tracking Identifier propagated.")
                    }
                }
            }
        }
    }
}
