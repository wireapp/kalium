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
import com.wire.kalium.logic.data.conversation.ConversationGuestLink
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveGuestRoomLinkUseCaseTest {

    @Mock
    val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

    private lateinit var observeGuestRoomLink: ObserveGuestRoomLinkUseCase

    @BeforeTest
    fun setUp() {
        observeGuestRoomLink = ObserveGuestRoomLinkUseCaseImpl(conversationGroupRepository)
    }

    @Test
    fun givenRepositoryEmitsValues_whenObservingGuestRoomLink_thenPropagateTheLink() = runTest {
        val guestLink = ConversationGuestLink("link", false)
        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::observeGuestRoomLink)
            .whenInvokedWith(eq(conversationId))
            .thenReturn(flowOf(Either.Right(guestLink)))

        observeGuestRoomLink(conversationId).first().shouldSucceed {
            assertEquals(guestLink, it)
        }

        verify(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::observeGuestRoomLink)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }
}
