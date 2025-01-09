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
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class InCallReactionsRepositoryTest {
    @Test
    fun whenNewReactionIsAdded_thenRepositoryEmitsNewReactionMessage() = runBlocking {

        // given
        val repository: InCallReactionsRepository = InCallReactionsDataSource()

        repository.observeInCallReactions(TestConversation.id()).test {

            // when
            repository.addInCallReaction(TestConversation.id(), TestUser.USER_ID, setOf("1"))

            // then
            assertEquals(InCallReactionMessage(TestConversation.id(), TestUser.USER_ID, setOf("1")), awaitItem())
        }
    }
}
