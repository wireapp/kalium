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
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.createEventProcessingLogger

public fun interface UserDeleteEventHandler {
    public suspend fun handle(event: Event.User.UserDelete): Either<CoreFailure, Unit>
}

public class UserDeleteEventHandlerImpl public constructor(
    private val selfUserId: UserId,
    private val userRepository: UserEventRepository,
    private val logoutDeletedAccount: suspend () -> Unit,
) : UserDeleteEventHandler {
    override suspend fun handle(event: Event.User.UserDelete): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return if (selfUserId == event.userId) {
            logoutDeletedAccount()
            Either.Right(Unit)
        } else {
            userRepository.markUserDeletedForEvent(event.userId)
                .onSuccess { logger.logSuccess() }
                .onFailure { logger.logFailure(it) }
        }
    }
}
