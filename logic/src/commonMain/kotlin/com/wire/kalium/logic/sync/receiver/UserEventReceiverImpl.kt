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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.sync.receiver.user.ClientRemoveEventHandler
import com.wire.kalium.logic.sync.receiver.user.NewClientEventHandler
import com.wire.kalium.logic.sync.receiver.user.NewConnectionEventHandler
import com.wire.kalium.logic.sync.receiver.user.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.user.UserDeleteEventHandler
import com.wire.kalium.logic.sync.receiver.user.UserUpdateEventHandler

/** Logic-owned router for the shared user receiver contract. */
@Suppress("LongParameterList")
internal class UserEventReceiverImpl internal constructor(
    private val newConnectionEventHandler: NewConnectionEventHandler,
    private val clientRemoveEventHandler: ClientRemoveEventHandler,
    private val userDeleteEventHandler: UserDeleteEventHandler,
    private val userUpdateEventHandler: UserUpdateEventHandler,
    private val newClientEventHandler: NewClientEventHandler,
    private val legalHoldRequestHandler: LegalHoldRequestHandler,
    private val legalHoldHandler: LegalHoldHandler,
    private val sessionRefreshSuggestedEventHandler: SessionRefreshSuggestedEventHandler,
) : UserEventReceiver {

    override suspend fun onEvent(
        transactionContext: CryptoTransactionContext,
        event: Event.User,
        deliveryInfo: EventDeliveryInfo
    ): Either<CoreFailure, Unit> = when (event) {
        is Event.User.NewConnection -> newConnectionEventHandler.handle(transactionContext, event, deliveryInfo)
        is Event.User.ClientRemove -> clientRemoveEventHandler.handle(event)
        is Event.User.UserDelete -> userDeleteEventHandler.handle(event)
        is Event.User.Update -> userUpdateEventHandler.handle(event)
        is Event.User.NewClient -> newClientEventHandler.handle(event)
        is Event.User.LegalHoldRequest -> legalHoldRequestHandler.handle(event)
        is Event.User.LegalHoldEnabled -> legalHoldHandler.handleEnable(event)
        is Event.User.LegalHoldDisabled -> legalHoldHandler.handleDisable(event)
        is Event.User.SessionRefreshSuggested -> sessionRefreshSuggestedEventHandler.handle(event, deliveryInfo)
    }
}
