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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.sync.incremental.EventSource
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserPropertiesEventReceiverImplTest {

    @Test
    fun givenReadReceiptEvent_whenReceived_thenTheSharedRepositoryIsUpdated() = runTest {
        val repository = mock<UserPropertiesConfigRepository> {
            everySuspend { setReadReceiptsStatus(eq(true)) } returns Either.Right(Unit)
        }
        val receiver = UserPropertiesEventReceiverImpl(
            repository,
            mock<UserPropertiesFolderRepository>(),
        )

        val result = receiver.onEvent(
            mock<CryptoTransactionContext>(),
            Event.UserProperty.ReadReceiptModeSet("event-id", true),
            EventDeliveryInfo(EventSource.LIVE),
        )

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { repository.setReadReceiptsStatus(eq(true)) }
    }

    @Test
    fun givenTypingIndicatorEvent_whenPersistenceFails_thenTheFailureIsPropagated() = runTest {
        val failure = com.wire.kalium.common.error.StorageFailure.DataNotFound
        val repository = mock<UserPropertiesConfigRepository> {
            everySuspend { setTypingIndicatorStatus(eq(false)) } returns Either.Left(failure)
        }
        val receiver = UserPropertiesEventReceiverImpl(
            repository,
            mock<UserPropertiesFolderRepository>(),
        )

        val result = receiver.onEvent(
            mock<CryptoTransactionContext>(),
            Event.UserProperty.TypingIndicatorModeSet("event-id", false),
            EventDeliveryInfo(EventSource.PENDING),
        )

        assertEquals(Either.Left(failure), result)
    }
}
