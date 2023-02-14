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
package com.wire.kalium.logic.feature.conversation.guestroomlink

import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetGuestRoomLinkUseCaseTest {

    @Mock
    val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

    private lateinit var getGuestRoomLink: GetGuestRoomLinkUseCase

    @BeforeTest
    fun setUp() {
        getGuestRoomLink = GetGuestRoomLinkUseCaseImpl(conversationGroupRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenRepositoryReturnsSuccess_whenGettingGuestRoomLink_thenReturnTheLink() = runTest {
        val guestLink = "www.wire.com"
        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::getGuestRoomLink)
            .whenInvokedWith(eq(conversationId))
            .thenReturn(guestLink)

        val result = getGuestRoomLink(conversationId)

        assertIs<GetGuestRoomLinkResult.Success>(result)
        assertEquals(guestLink, result.link)
        verify(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::getGuestRoomLink)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)


    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenRepositoryReturnsNull_whenGettingGuestRoomLink_thenPropagateFailure() = runTest {
        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::getGuestRoomLink)
            .whenInvokedWith(eq(conversationId))
            .thenReturn(null)

        val result = getGuestRoomLink(conversationId)

        assertIs<GetGuestRoomLinkResult.Failure>(result)
        verify(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::getGuestRoomLink)
            .with(any())
            .wasInvoked(exactly = once)
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }
}
