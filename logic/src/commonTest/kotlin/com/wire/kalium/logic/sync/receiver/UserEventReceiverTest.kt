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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.sync.receiver.user.ClientRemoveEventHandler
import com.wire.kalium.logic.sync.receiver.user.NewClientEventHandler
import com.wire.kalium.logic.sync.receiver.user.NewConnectionEventHandler
import com.wire.kalium.logic.sync.receiver.user.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.user.UserDeleteEventHandler
import com.wire.kalium.logic.sync.receiver.user.UserUpdateEventHandler
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserEventReceiverTest {
    @Test
    fun givenNewConnectionEvent_whenReceived_thenRoutesToNewConnectionHandler() = runTest {
        val event = TestEvent.newConnection()
        val arrangement = Arrangement()
        everySuspend {
            arrangement.newConnectionEventHandler.handle(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
        } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newConnectionEventHandler.handle(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
        }
    }

    @Test
    fun givenClientRemoveEvent_whenReceived_thenRoutesToClientRemoveHandler() = runTest {
        val event = TestEvent.clientRemove(clientId = ClientId("client"))
        val arrangement = Arrangement()
        everySuspend { arrangement.clientRemoveEventHandler.handle(event) } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.clientRemoveEventHandler.handle(event) }
    }

    @Test
    fun givenUserDeleteEvent_whenReceived_thenRoutesToUserDeleteHandler() = runTest {
        val event = TestEvent.userDelete(userId = TestUser.OTHER_USER_ID)
        val arrangement = Arrangement()
        everySuspend { arrangement.userDeleteEventHandler.handle(event) } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userDeleteEventHandler.handle(event) }
    }

    @Test
    fun givenUserUpdateEvent_whenReceived_thenRoutesToUserUpdateHandler() = runTest {
        val event = TestEvent.updateUser(userId = TestUser.OTHER_USER_ID)
        val arrangement = Arrangement()
        everySuspend { arrangement.userUpdateEventHandler.handle(event) } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userUpdateEventHandler.handle(event) }
    }

    @Test
    fun givenNewClientEvent_whenReceived_thenRoutesToNewClientHandler() = runTest {
        val event = TestEvent.newClient()
        val arrangement = Arrangement()
        everySuspend { arrangement.newClientEventHandler.handle(event) } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.newClientEventHandler.handle(event) }
    }

    @Test
    fun givenLegalHoldRequestEvent_whenReceived_thenRoutesToLegalHoldRequestHandler() = runTest {
        val event = Event.User.LegalHoldRequest(
            id = "event-id",
            clientId = ClientId("client"),
            lastPreKey = LastPreKey(1, "key"),
            userId = TestUser.SELF.id,
        )
        val arrangement = Arrangement()
        everySuspend { arrangement.legalHoldRequestHandler.handle(event) } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.legalHoldRequestHandler.handle(event) }
    }

    @Test
    fun givenLegalHoldEnabledEvent_whenReceived_thenRoutesToLegalHoldEnableHandler() = runTest {
        val event = Event.User.LegalHoldEnabled("event-id", TestUser.OTHER_USER_ID)
        val arrangement = Arrangement()
        everySuspend { arrangement.legalHoldHandler.handleEnable(event) } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.legalHoldHandler.handleEnable(event) }
    }

    @Test
    fun givenLegalHoldDisabledEvent_whenReceived_thenRoutesToLegalHoldDisableHandler() = runTest {
        val event = Event.User.LegalHoldDisabled("event-id", TestUser.OTHER_USER_ID)
        val arrangement = Arrangement()
        everySuspend { arrangement.legalHoldHandler.handleDisable(event) } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.legalHoldHandler.handleDisable(event) }
    }

    @Test
    fun givenSessionRefreshEvent_whenReceived_thenRoutesEventAndDeliveryInfoToHandler() = runTest {
        val event = TestEvent.sessionRefreshSuggested()
        val arrangement = Arrangement()
        everySuspend {
            arrangement.sessionRefreshSuggestedEventHandler.handle(event, TestEvent.nonLiveDeliveryInfo)
        } returns Either.Right(Unit)

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, TestEvent.nonLiveDeliveryInfo)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRefreshSuggestedEventHandler.handle(event, TestEvent.nonLiveDeliveryInfo)
        }
    }

    private class Arrangement {
        val transactionContext = mock<CryptoTransactionContext>()
        val newConnectionEventHandler = mock<NewConnectionEventHandler>(mode = MockMode.autoUnit)
        val clientRemoveEventHandler = mock<ClientRemoveEventHandler>(mode = MockMode.autoUnit)
        val userDeleteEventHandler = mock<UserDeleteEventHandler>(mode = MockMode.autoUnit)
        val userUpdateEventHandler = mock<UserUpdateEventHandler>(mode = MockMode.autoUnit)
        val newClientEventHandler = mock<NewClientEventHandler>(mode = MockMode.autoUnit)
        val legalHoldRequestHandler = mock<LegalHoldRequestHandler>(mode = MockMode.autoUnit)
        val legalHoldHandler = mock<LegalHoldHandler>(mode = MockMode.autoUnit)
        val sessionRefreshSuggestedEventHandler = mock<SessionRefreshSuggestedEventHandler>(mode = MockMode.autoUnit)

        val receiver = UserEventReceiverImpl(
            newConnectionEventHandler = newConnectionEventHandler,
            clientRemoveEventHandler = clientRemoveEventHandler,
            userDeleteEventHandler = userDeleteEventHandler,
            userUpdateEventHandler = userUpdateEventHandler,
            newClientEventHandler = newClientEventHandler,
            legalHoldRequestHandler = legalHoldRequestHandler,
            legalHoldHandler = legalHoldHandler,
            sessionRefreshSuggestedEventHandler = sessionRefreshSuggestedEventHandler,
        )
    }
}
