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
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.framework.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CallModerationActionsRepositoryTest {

    @Test
    fun givenACall_whenMutedActionIsAddedBeforeObserving_thenEmitThatAction() = runTest {
        val action1 = CallModerationAction("1", TestUser.USER_ID, CallModerationAction.Type.MUTED)
        val (_, repository) = Arrangement().arrange()

        // when action added before observing
        repository.addAction(MockConversation.ID, action1)
        advanceUntilIdle()

        repository.observeActions(MockConversation.ID).test {
            // then the action should be emitted
            assertEquals(action1, awaitItem())
        }
    }

    @Test
    fun givenACall_whenMutedActionIsAddedWhileObserving_thenEmitThatAction() = runTest {
        val action1 = CallModerationAction("1", TestUser.USER_ID, CallModerationAction.Type.MUTED)
        val (_, repository) = Arrangement().arrange()
        advanceUntilIdle()

        repository.observeActions(MockConversation.ID).test {
            // no action added yet, so no emission expected
            expectNoEvents()

            // when action added while observing
            repository.addAction(MockConversation.ID, action1)
            // then the action should be emitted
            assertEquals(action1, awaitItem())
        }
    }

    @Test
    fun givenACallWithSomeMutedAction_whenNewMutedActionIsAddedBeforeObserving_thenEmitOnlyNewAction() = runTest {
        val action1 = CallModerationAction("1", TestUser.USER_ID, CallModerationAction.Type.MUTED)
        val action2 = CallModerationAction("2", TestUser.OTHER_USER_ID, CallModerationAction.Type.MUTED)
        val (_, repository) = Arrangement().arrange()
        // action added initially before observing
        repository.addAction(MockConversation.ID, action1)
        advanceUntilIdle()

        // when new action added before observing
        repository.addAction(MockConversation.ID, action2)
        advanceUntilIdle()

        repository.observeActions(MockConversation.ID).test {
            // then only the latest action should be emitted, as the channel is conflated
            assertEquals(action2, awaitItem())
        }
    }

    @Test
    fun givenACallWithSomeMutedAction_whenNewMutedActionIsAddedWhileObserving_thenEmitOldAndThenNewAction() = runTest {
        val action1 = CallModerationAction("1", TestUser.USER_ID, CallModerationAction.Type.MUTED)
        val action2 = CallModerationAction("2", TestUser.OTHER_USER_ID, CallModerationAction.Type.MUTED)
        val (_, repository) = Arrangement().arrange()
        // action added initially before observing
        repository.addAction(MockConversation.ID, action1)
        advanceUntilIdle()

        repository.observeActions(MockConversation.ID).test {
            // first initial action should be emitted, as it was added before observing
            assertEquals(action1, awaitItem())

            // when new action is added while observing
            repository.addAction(MockConversation.ID, action2)
            // then new action should be emitted
            assertEquals(action2, awaitItem())
        }
    }

    @Test
    fun givenACallWithSomeMutedAction_whenActionsAreClearedForThatCall_thenDoNotEmitAnyAction() = runTest {
        val action1 = CallModerationAction("1", TestUser.USER_ID, CallModerationAction.Type.MUTED)
        val (_, repository) = Arrangement().arrange()
        // action added initially before observing
        repository.addAction(MockConversation.ID, action1)
        advanceUntilIdle()
        // actions cleared before observing
        repository.clearActions(MockConversation.ID)
        advanceUntilIdle()

        repository.observeActions(MockConversation.ID).test {
            // no action should be emitted, as previous ones were cleared
            expectNoEvents()
        }
    }

    inner class Arrangement {
        internal fun arrange() = this to CallModerationActionsDataSource()
    }
}
