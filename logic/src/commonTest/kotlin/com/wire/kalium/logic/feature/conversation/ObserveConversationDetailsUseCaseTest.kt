package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveConversationDetailsUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    @Mock
    private val syncManager: SyncManager = mock(SyncManager::class)

    private lateinit var observeConversationsUseCase: ObserveConversationDetailsUseCase

    @BeforeTest
    fun setup() {
        observeConversationsUseCase = ObserveConversationDetailsUseCase(conversationRepository, syncManager)
    }

    @Test
    fun givenAConversationId_whenObservingConversationUseCase_thenTheConversationRepositoryShouldBeCalledWithTheCorrectID() = runTest {
        val conversationId = TestConversation.ID
        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(anything())
            .then { flowOf() }

        observeConversationsUseCase(conversationId)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationID_whenObservingConversationUseCase_thenSyncManagerShouldBeCalled() = runTest {
        val conversationId = TestConversation.ID

        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(anything())
            .then { flowOf() }

        observeConversationsUseCase(conversationId)

        verify(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenTheConversationIsUpdated_whenObservingConversationUseCase_thenThisUpdateIsPropagatedInTheFlow() = runTest {
        val conversation = TestConversation.GROUP
        val conversationDetailsValues = listOf(
            ConversationDetails.Group(conversation, LegalHoldStatus.DISABLED),
            ConversationDetails.Group(conversation.copy(name = "New Name"), LegalHoldStatus.DISABLED)
        )

        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(anything())
            .then { conversationDetailsValues.asFlow() }

        observeConversationsUseCase(TestConversation.ID).test {
            assertEquals(conversationDetailsValues[0], awaitItem())
            assertEquals(conversationDetailsValues[1], awaitItem())
            awaitComplete()
        }
    }
}
