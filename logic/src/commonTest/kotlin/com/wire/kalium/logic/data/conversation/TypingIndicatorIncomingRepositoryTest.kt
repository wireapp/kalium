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

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TypingIndicatorIncomingRepositoryTest {

    @Test
    fun givenUsersInOneConversation_whenTheyAreTyping_thenAddItToTheListOfUsersTypingInConversation() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus()
                .arrange()

            typingIndicatorRepository.addTypingUserInConversation(conversationOne, expectedUserTypingOne)
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, expectedUserTypingTwo)

            assertEquals(
                setOf(expectedUserTypingOne, expectedUserTypingTwo),
                typingIndicatorRepository.observeUsersTyping(conversationOne).firstOrNull()?.map { it }?.toSet()
            )
            coVerify {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }.wasInvoked()
        }

    @Test
    fun givenUsersOneAndTwoTypingInAConversation_whenOneStopped_thenShouldNotBePresentInTypingUsersInConversation() =
        runTest(TestKaliumDispatcher.default) {
            val expectedUserTyping = setOf(TestConversation.USER_2)
            val (arrangement, typingIndicatorRepository) = Arrangement().withTypingIndicatorStatus().arrange()

            typingIndicatorRepository.addTypingUserInConversation(conversationOne, TestConversation.USER_1)
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, TestConversation.USER_2)
            typingIndicatorRepository.removeTypingUserInConversation(conversationOne, TestConversation.USER_1)

            assertEquals(
                expectedUserTyping,
                typingIndicatorRepository.observeUsersTyping(conversationOne).firstOrNull()?.map { it }?.toSet()
            )
            coVerify {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }.wasInvoked()
        }

    @Test
    fun givenMultipleUsersInDifferentConversations_whenTheyAreTyping_thenShouldBePresentInTypingUsersInEachConversation() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement().withTypingIndicatorStatus().arrange()
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, expectedUserTypingOne)
            typingIndicatorRepository.addTypingUserInConversation(conversationTwo, expectedUserTypingTwo)

            assertEquals(
                setOf(expectedUserTypingOne),
                typingIndicatorRepository.observeUsersTyping(conversationOne).firstOrNull()?.map { it }?.toSet()
            )
            assertEquals(
                setOf(expectedUserTypingTwo),
                typingIndicatorRepository.observeUsersTyping(conversationTwo).firstOrNull()?.map { it }?.toSet()
            )
            coVerify {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }.wasInvoked()
        }

    @Test
    fun givenUsersTypingInAConversation_whenClearExpiredItsCalled_thenShouldNotBePresentAnyInCached() =
        runTest(TestKaliumDispatcher.default) {
            val expectedUserTyping = setOf<UserId>()
            val (_, typingIndicatorRepository) = Arrangement().withTypingIndicatorStatus().arrange()

            typingIndicatorRepository.addTypingUserInConversation(conversationOne, TestConversation.USER_1)
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, TestConversation.USER_2)

            typingIndicatorRepository.clearExpiredTypingIndicators()

            assertEquals(
                expectedUserTyping,
                typingIndicatorRepository.observeUsersTyping(conversationOne).firstOrNull()?.map { it }?.toSet()
            )
        }

    private class Arrangement {
        @Mock
        val userPropertyRepository: UserPropertyRepository = mock(UserPropertyRepository::class)

        suspend fun withTypingIndicatorStatus(enabled: Boolean = true) = apply {
            coEvery {
                userPropertyRepository.getTypingIndicatorStatus()
            }.returns(enabled)
        }

        fun arrange() = this to TypingIndicatorIncomingRepositoryImpl(
            userTypingCache = ConcurrentMutableMap(),
            userPropertyRepository = userPropertyRepository
        )
    }

    private companion object {
        val conversationOne = TestConversation.ID
        val conversationTwo = TestConversation.ID.copy(value = "convo-two")

        val expectedUserTypingOne = TestConversation.USER_1
        val expectedUserTypingTwo = TestConversation.USER_2
    }
}
