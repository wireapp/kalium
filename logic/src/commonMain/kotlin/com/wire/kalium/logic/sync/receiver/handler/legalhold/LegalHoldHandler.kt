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
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.PersistOtherUserClientsUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal interface LegalHoldHandler {
    suspend fun handleEnable(legalHoldEnable: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit>
    suspend fun handleDisable(legalHoldEnable: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit>
}

internal class LegalHoldHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val persistOtherUserClients: PersistOtherUserClientsUseCase,
    private val fetchSelfClientsFromRemote: FetchSelfClientsFromRemoteUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val coroutineContext: CoroutineContext,
    private val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext)
) : LegalHoldHandler {
    override suspend fun handleEnable(legalHoldEnable: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold enabled for user ${legalHoldEnable.userId.toLogString()}")
        processEvent(selfUserId, legalHoldEnable.userId)
        return Either.Right(Unit)
    }

    override suspend fun handleDisable(legalHoldEnable: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold disabled for user ${legalHoldEnable.userId.toLogString()}")
        processEvent(selfUserId, legalHoldEnable.userId)
        return Either.Right(Unit)
    }

    private suspend fun processEvent(selfUserId: UserId, userId: UserId) {
        if (selfUserId == userId) {
            userConfigRepository.deleteLegalHoldRequest()
            coroutineScope.launch {
                fetchSelfClientsFromRemote()
                userConfigRepository.setLegalHoldChangeNotified(false)
            }
        } else {
            coroutineScope.launch {
                persistOtherUserClients(userId)
            }
        }
    }
}
