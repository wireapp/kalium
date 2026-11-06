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
package com.wire.kalium.logic.data.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.framework.TestConversation
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorIncomingRepositoryTest {

    @Test
    fun givenUsersInOneConversation_whenTheyAreTyping_thenAddItToTheListOfUsersTypingInConversation() = runTest {
        val (arrangement, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.addTypingUserInConversation(conversationOne, userOne)
        repository.addTypingUserInConversation(conversationOne, userTwo)

        assertEquals(setOf(userOne, userTwo), repository.observeUsersTyping(conversationOne).first())
        verifySuspend(VerifyMode.atLeast(1)) {
            arrangement.userPropertyRepository.getTypingIndicatorStatus()
        }
    }

    @Test
    fun givenUsersOneAndTwoTypingInAConversation_whenOneStopped_thenShouldNotBePresentInTypingUsersInConversation() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.addTypingUserInConversation(conversationOne, userOne)
        repository.addTypingUserInConversation(conversationOne, userTwo)
        repository.removeTypingUserInConversation(conversationOne, userOne)

        assertEquals(setOf(userTwo), repository.observeUsersTyping(conversationOne).first())
    }

    @Test
    fun givenMultipleUsersInDifferentConversations_whenTheyAreTyping_thenShouldBePresentInTypingUsersInEachConversation() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.addTypingUserInConversation(conversationOne, userOne)
        repository.addTypingUserInConversation(conversationTwo, userTwo)

        assertEquals(setOf(userOne), repository.observeUsersTyping(conversationOne).first())
        assertEquals(setOf(userTwo), repository.observeUsersTyping(conversationTwo).first())
    }

    @Test
    fun givenUsersTypingInAConversation_whenClearExpiredItsCalled_thenShouldNotBePresentAnyInCached() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.addTypingUserInConversation(conversationOne, userOne)
        repository.addTypingUserInConversation(conversationTwo, userTwo)
        repository.clearExpiredTypingIndicators()

        assertEquals(emptySet(), repository.observeUsersTyping(conversationOne).first())
        assertEquals(emptySet(), repository.observeUsersTyping(conversationTwo).first())
    }

    @Test
    fun disabledTypingStillRemovesExistingUsersButDoesNotAddNewUsers() = runTest {
        val (arrangement, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()
        repository.addTypingUserInConversation(conversationOne, userOne)
        arrangement.withTypingIndicatorStatus(false)
        repository.addTypingUserInConversation(conversationOne, userTwo)
        assertEquals(setOf(userOne), repository.observeUsersTyping(conversationOne).first())
        repository.removeTypingUserInConversation(conversationOne, userOne)
        assertEquals(emptySet(), repository.observeUsersTyping(conversationOne).first())
    }

    @Test
    fun givenUserTyping_whenTimeoutElapses_thenEmitEmptySet() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.observeUsersTyping(conversationOne).test {
            assertEquals(emptySet(), awaitItem())
            repository.addTypingUserInConversation(conversationOne, userOne)
            assertEquals(setOf(userOne), awaitItem())

            advanceTimeBy(59_999)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun givenUserTyping_whenStartedIsRefreshedAtFiftySeconds_thenExpireSixtySecondsAfterRefreshWithoutReEmitting() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.observeUsersTyping(conversationOne).test {
            assertEquals(emptySet(), awaitItem())
            repository.addTypingUserInConversation(conversationOne, userOne)
            assertEquals(setOf(userOne), awaitItem())

            advanceTimeBy(50_000)
            repository.addTypingUserInConversation(conversationOne, userOne)
            runCurrent()
            expectNoEvents() // same set of users, so no duplicate emission

            advanceTimeBy(59_999)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun givenTwoUsersStartedAtDifferentTimes_whenTimeoutsElapse_thenExpireIndependently() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.observeUsersTyping(conversationOne).test {
            assertEquals(emptySet(), awaitItem())
            repository.addTypingUserInConversation(conversationOne, userOne)
            assertEquals(setOf(userOne), awaitItem())

            advanceTimeBy(20_000)
            repository.addTypingUserInConversation(conversationOne, userTwo)
            assertEquals(setOf(userOne, userTwo), awaitItem())

            advanceTimeBy(40_000)
            runCurrent()
            assertEquals(setOf(userTwo), awaitItem())

            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun givenUserTyping_whenStoppedBeforeTimeout_thenRemoveImmediatelyAndNothingIsEmittedLater() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.observeUsersTyping(conversationOne).test {
            assertEquals(emptySet(), awaitItem())
            repository.addTypingUserInConversation(conversationOne, userOne)
            assertEquals(setOf(userOne), awaitItem())

            advanceTimeBy(30_000)
            repository.removeTypingUserInConversation(conversationOne, userOne)
            assertEquals(emptySet(), awaitItem())

            advanceTimeBy(60_000)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun givenUnobservedUserTyping_whenObservedAfterTimeout_thenEmitEmptySet() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.addTypingUserInConversation(conversationOne, userOne)
        advanceTimeBy(60_000)

        assertEquals(emptySet(), repository.observeUsersTyping(conversationOne).first())
    }

    @Test
    fun givenUnobservedUserTyping_whenObservedMidTimeout_thenExpireAtOriginalDeadline() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()
        repository.addTypingUserInConversation(conversationOne, userOne)
        advanceTimeBy(30_000)

        repository.observeUsersTyping(conversationOne).test {
            assertEquals(setOf(userOne), awaitItem())

            advanceTimeBy(29_999)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun givenActiveObserver_whenSyncClearsIndicators_thenObserverKeepsReceivingNewUpdates() = runTest {
        val (_, repository) = Arrangement(this).withTypingIndicatorStatus().arrange()

        repository.observeUsersTyping(conversationOne).test {
            assertEquals(emptySet(), awaitItem())
            repository.addTypingUserInConversation(conversationOne, userOne)
            assertEquals(setOf(userOne), awaitItem())

            repository.clearExpiredTypingIndicators()
            assertEquals(emptySet(), awaitItem())

            repository.addTypingUserInConversation(conversationOne, userTwo)
            assertEquals(setOf(userTwo), awaitItem())
        }
    }

    private class Arrangement(private val testScope: TestScope) {
        val userPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)

        suspend fun withTypingIndicatorStatus(enabled: Boolean = true) = apply {
            everySuspend {
                userPropertyRepository.getTypingIndicatorStatus()
            }.returns(enabled)
        }

        fun arrange() = this to TypingIndicatorIncomingRepositoryImpl(
            userPropertyRepository = userPropertyRepository,
            timeSource = testScope.testTimeSource
        )
    }

    private companion object {
        val conversationOne = TestConversation.ID
        val conversationTwo = TestConversation.ID.copy(value = "convo-two")

        val userOne = TestConversation.USER_1
        val userTwo = TestConversation.USER_2
    }
}
