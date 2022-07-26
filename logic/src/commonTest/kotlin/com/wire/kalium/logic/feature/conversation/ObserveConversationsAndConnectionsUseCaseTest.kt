package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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

}
