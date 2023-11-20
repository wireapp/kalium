/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger

interface LegalHoldRequestHandler {
    fun handle(legalHoldRequest: Event.User.LegalHoldRequest): Either<CoreFailure, Unit>
}

class LegalHoldRequestHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val userConfigRepository: UserConfigRepository
) : LegalHoldRequestHandler {
    override fun handle(legalHoldRequest: Event.User.LegalHoldRequest): Either<CoreFailure, Unit> {
        if (selfUserId == legalHoldRequest.userId) {
            kaliumLogger.i("Legal hold request received, storing it locally..")
            userConfigRepository.setLegalHoldRequest(
                legalHoldRequest.clientId.value,
                legalHoldRequest.lastPreKey.id,
                legalHoldRequest.lastPreKey.key
            )
        }
        return Either.Right(Unit)
    }
}
