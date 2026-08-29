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
package com.wire.kalium.logic.sync.receiver.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.util.createEventProcessingLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

public fun interface NewConnectionEventHandler {
    public suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.User.NewConnection,
        deliveryInfo: EventDeliveryInfo,
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
public class NewConnectionEventHandlerImpl public constructor(
    private val userRepository: NewConnectionEventUserRepository,
    private val connectionRepository: NewConnectionEventRepository,
    private val scheduleOneOnOneResolution: suspend (CryptoTransactionContext, UserId, Duration) -> Unit,
    private val persistUnverifiedWarning: suspend (ConversationId) -> Either<CoreFailure, Unit>,
    private val handleLegalHoldChange: suspend (Event.User.NewConnection) -> Either<CoreFailure, Unit>,
) : NewConnectionEventHandler {
    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.User.NewConnection,
        deliveryInfo: EventDeliveryInfo,
    ): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return userRepository.fetchUserForConnectionEvent(event.connection.qualifiedToId)
            .flatMap { fetchResult ->
                if (fetchResult == ConnectionUserFetchResult.NOT_FOUND) {
                    kaliumLogger.w("Ignoring missing user details while processing a connection event")
                }
                val previousStatus = connectionRepository
                    .getConnectionStatusForEvent(event.connection.qualifiedConversationId)
                    .getOrNull()
                connectionRepository.insertConnectionFromEvent(transactionContext, event)
                    .flatMap {
                        if (event.connection.status == ConnectionState.ACCEPTED) {
                            scheduleOneOnOneResolution(
                                transactionContext,
                                event.connection.qualifiedToId,
                                if (deliveryInfo.source == EventSource.LIVE) 3.seconds else ZERO,
                            )
                            if (previousStatus != ConnectionState.MISSING_LEGALHOLD_CONSENT) {
                                persistUnverifiedWarning(event.connection.qualifiedConversationId)
                            } else {
                                Either.Right(Unit)
                            }
                        } else {
                            Either.Right(Unit)
                        }
                    }
                    .flatMap { handleLegalHoldChange(event) }
            }
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }
}
