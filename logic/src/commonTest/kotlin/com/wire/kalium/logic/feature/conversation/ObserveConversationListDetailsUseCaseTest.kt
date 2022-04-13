package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ObserveConversationListDetailsUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    @Mock
    private val syncManager: SyncManager = mock(SyncManager::class)

    private lateinit var observeConversationsUseCase: ObserveConversationListDetailsUseCase

    @BeforeTest
    fun setup() {
        observeConversationsUseCase = ObserveConversationListDetailsUseCase(conversationRepository, syncManager)
    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationListShouldBeCalled() = runTest {
        val conversations = listOf(TestConversation.SELF, TestConversation.GROUP)

        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationsUseCase().collect()

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenSyncManagerShouldBeCalled() = runTest {
        val conversations = listOf(TestConversation.SELF, TestConversation.GROUP)

        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationsUseCase().collect()

        verify(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationDetailsShouldBeCalledForEachID() = runTest {
        val conversations = listOf(TestConversation.SELF, TestConversation.GROUP)

        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationsUseCase().collect()

        conversations.forEach { conversation ->
            verify(conversationRepository)
                .suspendFunction(conversationRepository::getConversationDetailsById)
                .with(eq(conversation.id))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSomeConversationsDetailsAreUpdated_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        val oneOnOneConversation = TestConversation.ONE_ON_ONE
        val groupConversation = TestConversation.GROUP
        val conversations = listOf(groupConversation, oneOnOneConversation)

        val groupConversationUpdates = listOf(ConversationDetails.Group(groupConversation))
        val firstOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER,
            ConnectionState.ACCEPTED,
            LegalHoldStatus.ENABLED
        )
        val secondOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER.copy(name = "New User Name"),
            ConnectionState.PENDING,
            LegalHoldStatus.DISABLED
        )
        val oneOnOneConversationDetailsUpdates = listOf(
            firstOneOnOneDetails,
            secondOneOnOneDetails
        )

        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(eq(groupConversation.id))
            .thenReturn(groupConversationUpdates.asFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(eq(oneOnOneConversation.id))
            .thenReturn(oneOnOneConversationDetailsUpdates.asFlow())

        observeConversationsUseCase().test {
            assertContentEquals(groupConversationUpdates + firstOneOnOneDetails, awaitItem())
            assertContentEquals(groupConversationUpdates + secondOneOnOneDetails, awaitItem())
            awaitComplete()
        }
    }


    @Test
    fun givenAConversationIsAddedToTheList_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        val groupConversation = TestConversation.GROUP
        val groupConversationDetails = ConversationDetails.Group(groupConversation)

        val selfConversation = TestConversation.SELF
        val selfConversationDetails = ConversationDetails.Self(selfConversation)

        val firstConversationsList = listOf(groupConversation)
        val secondConversationsList = firstConversationsList + selfConversation
        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(conversationListUpdates.consumeAsFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(eq(groupConversation.id))
            .thenReturn(flowOf(groupConversationDetails))

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(eq(selfConversation.id))
            .thenReturn(flowOf(selfConversationDetails))

        observeConversationsUseCase().test {
            assertContentEquals(listOf(groupConversationDetails), awaitItem())

            conversationListUpdates.send(secondConversationsList)
            assertContentEquals(listOf(groupConversationDetails, selfConversationDetails), awaitItem())

            conversationListUpdates.close()
            awaitComplete()
        }
    }
}
