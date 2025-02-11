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
package com.wire.kalium.logic.feature.conversation.guestroomlink

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class RevokeGuestRoomLinkUseCaseTest {

    @Mock
    val conversationGroupRepository = mock(ConversationGroupRepository::class)

    private lateinit var revokeGuestRoomLink: RevokeGuestRoomLinkUseCase

    @BeforeTest
    fun setUp() {
        revokeGuestRoomLink = RevokeGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )
    }

    @Test
    fun givenRepositoryReturnsSuccess_whenTryingToRevokeAGuestRoomLink_ThenReturnSuccess() = runTest {

        coEvery {
            conversationGroupRepository.revokeGuestRoomLink(any())
        }.returns(Either.Right(Unit))

        val result = revokeGuestRoomLink(conversationId)

        assertIs<RevokeGuestRoomLinkResult.Success>(result)
        coVerify {
            conversationGroupRepository.revokeGuestRoomLink(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryReturnsFailure_whenTryingToRevokeAGuestRoomLink_ThenReturnError() = runTest {

        coEvery {
            conversationGroupRepository.revokeGuestRoomLink(any())
        }.returns(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        val result = revokeGuestRoomLink(conversationId)

        assertIs<RevokeGuestRoomLinkResult.Failure>(result)
        coVerify {
            conversationGroupRepository.revokeGuestRoomLink(any())
        }.wasInvoked(exactly = once)
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }
}
