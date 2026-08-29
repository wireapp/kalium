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

import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NewClientEventHandlerTest {
    @Test
    fun givenNewClientEvent_NewClientManagerInvoked() = runTest {
        val event = newClientEvent()
        val repository = mock<NewClientEventRepository>(mode = MockMode.autoUnit) {
            everySuspend { saveNewClientEvent(event) } returns Either.Right(Unit)
        }
        val handler = NewClientEventHandlerImpl(repository) { Either.Right(CLIENT_ID_2) }

        handler.handle(event)

        verifySuspend(VerifyMode.exactly(1)) { repository.saveNewClientEvent(event) }
    }

    @Test
    fun givenNewClientEventIsSameAsCurrent_ThenSkipSavingEvent() = runTest {
        val event = newClientEvent()
        val repository = mock<NewClientEventRepository>(mode = MockMode.autoUnit)
        val handler = NewClientEventHandlerImpl(repository) { Either.Right(CLIENT_ID_1) }

        handler.handle(event)

        verifySuspend(VerifyMode.not) { repository.saveNewClientEvent(event) }
    }
}
