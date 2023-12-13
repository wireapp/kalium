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
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.firstOrNull

internal interface LegalHoldHandler {
    suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit>
    suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class LegalHoldHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val persistOtherUserClients: PersistOtherUserClientsUseCase,
    private val fetchSelfClientsFromRemote: FetchSelfClientsFromRemoteUseCase,
    private val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val legalHoldSystemMessagesHandler: LegalHoldSystemMessagesHandler,
) : LegalHoldHandler {
    override suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold enabled for user ${legalHoldEnabled.userId.toLogString()}")
        // check if the user has already been under legal hold prior to this event
        val userHasBeenUnderLegalHold = isUserUnderLegalHold(legalHoldEnabled.userId)
        // fetch and persist current clients for the given user
        processEvent(selfUserId, legalHoldEnabled.userId)
        // create system messages only if legal hold status has changed for the given user
        if (!userHasBeenUnderLegalHold) {
            legalHoldSystemMessagesHandler.handleEnable(legalHoldEnabled.userId)
        }

        return Either.Right(Unit)
    }

    override suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold disabled for user ${legalHoldDisabled.userId.toLogString()}")
        // check if the user has already been under legal hold prior to this event
        val userHasBeenUnderLegalHold = isUserUnderLegalHold(legalHoldDisabled.userId)
        // fetch and persist current clients for the given user
        processEvent(selfUserId, legalHoldDisabled.userId)
        // create system messages only if legal hold status has changed for the given user
        if (userHasBeenUnderLegalHold) {
            legalHoldSystemMessagesHandler.handleDisable(legalHoldDisabled.userId)
        }

        return Either.Right(Unit)
    }

    private suspend fun processEvent(selfUserId: UserId, userId: UserId) {
        if (selfUserId == userId) {
            userConfigRepository.deleteLegalHoldRequest()
            fetchSelfClientsFromRemote()
            userConfigRepository.setLegalHoldChangeNotified(false)
        } else {
            persistOtherUserClients(userId)
        }
    }

    private suspend fun isUserUnderLegalHold(userId: UserId): Boolean =
        observeLegalHoldStateForUser(userId).firstOrNull() == LegalHoldState.Enabled
}
