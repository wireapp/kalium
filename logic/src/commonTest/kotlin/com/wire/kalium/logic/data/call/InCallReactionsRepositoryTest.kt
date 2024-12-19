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
package com.wire.kalium.logic.data.call

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InCallReactionsRepositoryTest {

    val sendingUserId: QualifiedID = QualifiedID("userId", "domain")

    @Test
    fun whenNewReactionIsAdded_thenRepositoryEmitsNewReactionMessage() = runBlocking {

        // given
        val (_, repository) = Arrangement()
            .withUserAvailable()
            .arrange()

        repository.observeInCallReactions().test {

            // when
            repository.addInCallReaction(setOf("1"), sendingUserId)

            // then
            assertEquals(InCallReactionMessage(setOf("1"), sendingUserId, "TestUserName"), awaitItem())
        }
    }

    @Test
    fun whenUserIsNotFound_thenReactionRepositoryDoesNotEmitNewReactionMessage() = runTest(TestKaliumDispatcher.default) {

        // given
        val (_, repository) = Arrangement()
            .withUserNotAvailable()
            .arrange()

        repository.observeInCallReactions().test {

            // when
            repository.addInCallReaction(setOf("1"), sendingUserId)

            // then
            expectNoEvents()
        }
    }

    private class Arrangement {

        val user: OtherUser = OtherUser(
            id = QualifiedID("userId", "domain"),
            name = "TestUserName",
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            expiresAt = null,
            supportedProtocols = null,
            userType = UserType.INTERNAL,
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
        )

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        suspend fun withUserAvailable() = apply {
            coEvery {
                userRepository.userById(any())
            }.returns(Either.Right(user))
        }

        suspend fun withUserNotAvailable() = apply {
            coEvery {
                userRepository.userById(any())
            }.returns(Either.Left(CoreFailure.Unknown(IllegalStateException("Test user not found"))))
        }

        fun arrange() = this to InCallReactionsDataSource(
            userRepository = userRepository
        )
    }

}
