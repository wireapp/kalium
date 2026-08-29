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
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.util.createEventProcessingLogger

public fun interface ClientRemoveEventHandler {
    public suspend fun handle(event: Event.User.ClientRemove): Either<CoreFailure, Unit>
}

public class ClientRemoveEventHandlerImpl public constructor(
    private val currentClientIdProvider: suspend () -> Either<CoreFailure, ClientId>,
    private val logoutCurrentClient: suspend () -> Unit,
) : ClientRemoveEventHandler {
    override suspend fun handle(event: Event.User.ClientRemove): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return currentClientIdProvider().map { currentClientId ->
            if (currentClientId == event.clientId) {
                logger.logSuccess("info" to "CURRENT_CLIENT")
                logoutCurrentClient()
            } else {
                logger.logSuccess("info" to "OTHER_CLIENT")
            }
        }
    }
}
