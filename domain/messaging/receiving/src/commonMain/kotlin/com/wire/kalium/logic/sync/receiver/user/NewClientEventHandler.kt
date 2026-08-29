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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.util.createEventProcessingLogger

public fun interface NewClientEventHandler {
    public suspend fun handle(event: Event.User.NewClient): Either<CoreFailure, Unit>
}

public class NewClientEventHandlerImpl public constructor(
    private val clientRepository: NewClientEventRepository,
    private val currentClientIdProvider: suspend () -> Either<CoreFailure, ClientId>,
) : NewClientEventHandler {
    override suspend fun handle(event: Event.User.NewClient): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)

        if (shouldSkipCurrentClientId(event)) {
            logger.logSuccess()
            return Unit.right()
        }

        return clientRepository.saveNewClientEvent(event)
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }

    private suspend fun shouldSkipCurrentClientId(event: Event.User.NewClient): Boolean =
        currentClientIdProvider().fold(
            fnL = { false },
            fnR = { currentClientId -> currentClientId == event.client.id }
        )
}
