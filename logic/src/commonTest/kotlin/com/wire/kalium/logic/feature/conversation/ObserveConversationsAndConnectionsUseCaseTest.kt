package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObserveConversationsAndConnectionsUseCaseTest {

    @Mock
    val observeConversationListDetailsUseCase: ObserveConversationListDetailsUseCase =
        mock(ObserveConversationListDetailsUseCase::class)

    @Mock
    val observeConnectionListUseCase: ObserveConnectionListUseCase = mock(ObserveConnectionListUseCase::class)

    @Mock
    val syncManager: SyncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

    private lateinit var observeConversationsAndConnectionsUseCase: ObserveConversationsAndConnectionsUseCase

    @BeforeTest
    fun setup() {
        observeConversationsAndConnectionsUseCase =
            ObserveConversationsAndConnectionsUseCaseImpl(syncManager, observeConversationListDetailsUseCase, observeConnectionListUseCase)
    }

    @Test
    fun givenSomeConversationsAndConnections_whenObservingDetailsListAndConnections_thenObserveConversationListShouldBeCalled() = runTest {
        // given
        given(observeConversationListDetailsUseCase)
            .suspendFunction(observeConversationListDetailsUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(listOf(TestConversationDetails.CONVERSATION_ONE_ONE)))

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
            .thenReturn(flowOf(conversations))
        given(observeConnectionListUseCase)
            .suspendFunction(observeConnectionListUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(connections))
        // when
        val result = observeConversationsAndConnectionsUseCase().first()
        // then
        assertEquals(result, sorted)
    }
}
