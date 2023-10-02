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
package com.wire.kalium.logic.data.conversation

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TypingIndicatorRepositoryTest {

    private lateinit var typingIndicatorRepository: TypingIndicatorRepository

    @BeforeTest
    fun setUp() {
        typingIndicatorRepository = TypingIndicatorRepositoryImpl(ConcurrentMutableMap())
    }

    @Test
    fun givenUsersInOneConversation_whenTheyAreTyping_thenAddItToTheListOfUsersTypingInConversation() =
        runTest(TestKaliumDispatcher.default) {
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, expectedUserTypingOne)
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, expectedUserTypingTwo)

            assertEquals(
                setOf(expectedUserTypingOne, expectedUserTypingTwo),
                typingIndicatorRepository.observeUsersTyping(conversationOne).firstOrNull()?.map { it.userId }?.toSet()
            )
        }

    @Test
    fun givenUsersOneAndTwoTypingInAConversation_whenOneStopped_thenShouldNotBePresentInTypingUsersInConversation() =
        runTest(TestKaliumDispatcher.default) {
            val expectedUserTyping = setOf(TestConversation.USER_2)

            typingIndicatorRepository.addTypingUserInConversation(conversationOne, TestConversation.USER_1)
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, TestConversation.USER_2)
            typingIndicatorRepository.removeTypingUserInConversation(conversationOne, TestConversation.USER_1)

            assertEquals(
                expectedUserTyping,
                typingIndicatorRepository.observeUsersTyping(conversationOne).firstOrNull()?.map { it.userId }?.toSet()
            )
        }

    @Test
    fun givenMultipleUsersInDifferentConversations_whenTheyAreTyping_thenShouldBePresentInTypingUsersInEachConversation() =
        runTest(TestKaliumDispatcher.default) {
            typingIndicatorRepository.addTypingUserInConversation(conversationOne, expectedUserTypingOne)
            typingIndicatorRepository.addTypingUserInConversation(conversationTwo, expectedUserTypingTwo)

            assertEquals(
                setOf(expectedUserTypingOne),
                typingIndicatorRepository.observeUsersTyping(conversationOne).firstOrNull()?.map { it.userId }?.toSet()
            )
            assertEquals(
                setOf(expectedUserTypingTwo),
                typingIndicatorRepository.observeUsersTyping(conversationTwo).firstOrNull()?.map { it.userId }?.toSet()
            )
        }

    private companion object {
        val conversationOne = TestConversation.ID
        val conversationTwo = TestConversation.ID.copy(value = "convo-two")

        val expectedUserTypingOne = TestConversation.USER_1
        val expectedUserTypingTwo = TestConversation.USER_2
    }
}
