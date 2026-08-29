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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class UserUpdateEventHandlerTest {
    @Test
    fun givenUserUpdateEvent_RepoIsInvoked() = runTest {
        val event = userUpdateEvent()
        val repository = mock<UserUpdateEventRepository>(mode = MockMode.autoUnit) {
            everySuspend { updateUserFromEvent(event) } returns Either.Right(Unit)
        }
        val handler = UserUpdateEventHandlerImpl(repository)

        val result = handler.handle(event)

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) { repository.updateUserFromEvent(event) }
    }

    @Test
    fun givenUserUpdateEvent_whenUserIsNotFoundInLocalDB_thenShouldIgnoreThisEventFailure() = runTest {
        val event = userUpdateEvent(OTHER_USER_ID)
        val repository = mock<UserUpdateEventRepository> {
            everySuspend { updateUserFromEvent(event) } returns Either.Left(StorageFailure.DataNotFound)
        }

        val result = UserUpdateEventHandlerImpl(repository).handle(event)

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenUserUpdateEvent_whenFailsWitOtherError_thenShouldFail() = runTest {
        val event = userUpdateEvent(OTHER_USER_ID)
        val failure: CoreFailure = StorageFailure.Generic(Throwable("error"))
        val repository = mock<UserUpdateEventRepository> {
            everySuspend { updateUserFromEvent(event) } returns Either.Left(failure)
        }

        val result = UserUpdateEventHandlerImpl(repository).handle(event)

        assertIs<Either.Left<StorageFailure.Generic>>(result)
    }
}
