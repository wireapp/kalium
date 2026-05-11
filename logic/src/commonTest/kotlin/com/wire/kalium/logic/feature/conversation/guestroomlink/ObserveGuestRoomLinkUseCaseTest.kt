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

import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationGuestLink
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class ObserveGuestRoomLinkUseCaseTest {

    val conversationGroupRepository = mock<ConversationGroupRepository>(mode = MockMode.autoUnit)

    private lateinit var observeGuestRoomLink: ObserveGuestRoomLinkUseCase

    @BeforeTest
    fun setUp() {
        observeGuestRoomLink = ObserveGuestRoomLinkUseCaseImpl(conversationGroupRepository)
    }

    @Test
    fun givenRepositoryEmitsValues_whenObservingGuestRoomLink_thenPropagateTheLink() = runTest {
        val guestLink = ConversationGuestLink("link", false)
        everySuspend {
            conversationGroupRepository.observeGuestRoomLink(eq(conversationId))
        } returns flowOf(Either.Right(guestLink))

        val result = observeGuestRoomLink(conversationId).first()
        assertIs<ObserveGuestRoomLinkResult.Success>(result)
        assertEquals(guestLink, result.link)

        verifySuspend(VerifyMode.exactly(1)) {
            conversationGroupRepository.observeGuestRoomLink(eq(conversationId))
        }
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }
}
