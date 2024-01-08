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

package com.wire.kalium.logic.feature.conversation

// todo: apply below testCases from here to the new changes
class ObserveConversationsAndConnectionsUseCaseTest {

   /* @Mock
    val observeConversationListDetailsUseCase: ObserveConversationListDetailsUseCase =
        mock(ObserveConversationListDetailsUseCase::class)

    @Mock
    val observeConnectionListUseCase: ObserveConnectionListUseCase = mock(ObserveConnectionListUseCase::class)

    private lateinit var observeConversationsAndConnectionsUseCase: ObserveConversationsAndConnectionsUseCase

    @BeforeTest
    fun setup() {
        observeConversationsAndConnectionsUseCase =
            ObserveConversationsAndConnectionsUseCaseImpl(observeConversationListDetailsUseCase, observeConnectionListUseCase)
    }

    @Test
    fun givenSomeConversationsAndConnections_whenObservingDetailsListAndConnections_thenObserveConversationListShouldBeCalled() = runTest {
        // given
        given(observeConversationListDetailsUseCase)
            .suspendFunction(observeConversationListDetailsUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(ConversationListDetails(listOf(TestConversationDetails.CONVERSATION_ONE_ONE), 1)))

        given(observeConnectionListUseCase)
            .suspendFunction(observeConnectionListUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(listOf(TestConversationDetails.CONNECTION)))

        // when
        observeConversationsAndConnectionsUseCase().collect()

        // then
        verify(observeConversationListDetailsUseCase)
            .suspendFunction(observeConversationListDetailsUseCase::invoke)
            .wasInvoked(exactly = once)

        verify(observeConnectionListUseCase)
            .suspendFunction(observeConnectionListUseCase::invoke)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSomeConversationsAndConnections_whenObservingDetailsListAndConnectionsAndFails_thenObserveConversationListShouldThrowError() =
        runTest {
            // given
            given(observeConversationListDetailsUseCase)
                .suspendFunction(observeConversationListDetailsUseCase::invoke)
                .whenInvoked()
                .then { throw RuntimeException("Some error in my flow!") }

            // then
            assertFailsWith<RuntimeException> { observeConversationsAndConnectionsUseCase().collect() }
        }

    @Test
    fun givenSomeConversationsAndConnections_whenObservingDetailsListAndConnections_thenListShouldBeSorted() = runTest {
        // reverse chronologically, if both have the same time (or is unknown) then alphabetically, null names at the bottom
        // given
        val conversations = listOf(
            TestConversation.CONVERSATION.copy(lastModifiedDate = "2022.01.02", name = "C"),
            TestConversation.CONVERSATION.copy(lastModifiedDate = "2022.01.01", name = "Z"),
            TestConversation.CONVERSATION.copy(lastModifiedDate = "2022.01.02", name = null),
            TestConversation.CONVERSATION.copy(lastModifiedDate = "2022.01.02", name = "A")
        ).map { TestConversationDetails.CONVERSATION_ONE_ONE.copy(conversation = it) }
        val connections = listOf(
            TestConversationDetails.CONNECTION.copy(lastModifiedDate = "2022.01.01", otherUser = TestUser.OTHER.copy(name = "Y")),
            TestConversationDetails.CONNECTION.copy(lastModifiedDate = "2022.01.01", otherUser = TestUser.OTHER.copy(name = null)),
            TestConversationDetails.CONNECTION.copy(lastModifiedDate = "2022.01.02", otherUser = TestUser.OTHER.copy(name = "B")),
            TestConversationDetails.CONNECTION.copy(lastModifiedDate = "2022.01.02", otherUser = TestUser.OTHER.copy(name = "D"))
        )
        val sorted = listOf(
            conversations[3],
            connections[2],
            conversations[0],
            connections[3],
            conversations[2],
            connections[0],
            conversations[1],
            connections[1]
        )
        given(observeConversationListDetailsUseCase)
            .suspendFunction(observeConversationListDetailsUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(ConversationListDetails(conversations, unreadConversationsCount = 0)))
        given(observeConnectionListUseCase)
            .suspendFunction(observeConnectionListUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(connections))
        // when
        val result = observeConversationsAndConnectionsUseCase().first()
        // then
        assertEquals(result.conversationList, sorted)
    }*/
}
