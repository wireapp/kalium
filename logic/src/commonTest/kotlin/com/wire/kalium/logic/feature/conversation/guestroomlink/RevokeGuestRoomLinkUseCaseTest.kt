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
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class RevokeGuestRoomLinkUseCaseTest {

    @Mock
    val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

    private lateinit var revokeGuestRoomLink: RevokeGuestRoomLinkUseCase

    @BeforeTest
    fun setUp() {
        revokeGuestRoomLink = RevokeGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenRepositoryReturnsSuccess_whenTryingToRevokeAGuestRoomLink_ThenReturnSuccess() = runTest {

        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::revokeGuestRoomLink)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))

        val result = revokeGuestRoomLink(conversationId)

        assertIs<RevokeGuestRoomLinkResult.Success>(result)
        verify(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::revokeGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenRepositoryReturnsFailure_whenTryingToRevokeAGuestRoomLink_ThenReturnError() = runTest {

        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::revokeGuestRoomLink)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        val result = revokeGuestRoomLink(conversationId)

        assertIs<RevokeGuestRoomLinkResult.Failure>(result)
        verify(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::revokeGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }
}
