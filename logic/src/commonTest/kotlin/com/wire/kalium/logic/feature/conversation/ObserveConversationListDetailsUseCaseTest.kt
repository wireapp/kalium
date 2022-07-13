package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
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
import kotlin.test.assertEquals

class ObserveConversationListDetailsUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    @Mock
    private val callRepository: CallRepository = mock(CallRepository::class)

    @Mock
    private val syncManager: SyncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

    private lateinit var observeConversationsUseCase: ObserveConversationListDetailsUseCaseImpl

    @BeforeTest
    fun setup() {
        observeConversationsUseCase = ObserveConversationListDetailsUseCaseImpl(conversationRepository, syncManager, callRepository)
    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationListShouldBeCalled() = runTest {
        val conversations = listOf(TestConversation.SELF, TestConversation.GROUP())

        given(callRepository)
            .function(callRepository::ongoingCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationsUseCase().collect()

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenSyncManagerShouldBeCalled() = runTest {
        val conversations = listOf(TestConversation.SELF, TestConversation.GROUP())

        given(callRepository)
            .function(callRepository::ongoingCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationsUseCase().collect()

        verify(syncManager)
            .function(syncManager::startSyncIfIdle)
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationDetailsShouldBeCalledForEachID() = runTest {
        val conversations = listOf(TestConversation.SELF, TestConversation.GROUP())

        given(callRepository)
            .function(callRepository::ongoingCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationsUseCase().collect()

        conversations.forEach { conversation ->
            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .with(eq(conversation.id))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSomeConversationsDetailsAreUpdated_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        val oneOnOneConversation = TestConversation.ONE_ON_ONE
        val groupConversation = TestConversation.GROUP()
        val conversations = listOf(groupConversation, oneOnOneConversation)

        val groupConversationUpdates = listOf(ConversationDetails.Group(groupConversation, LegalHoldStatus.DISABLED))
        val firstOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER,
            ConnectionState.ACCEPTED,
            LegalHoldStatus.ENABLED,
            UserType.INTERNAL,
        )
        val secondOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER.copy(name = "New User Name"),
            ConnectionState.PENDING,
            LegalHoldStatus.DISABLED,
            UserType.INTERNAL,
        )

        val oneOnOneDetailsChannel = Channel<ConversationDetails.OneOne>(Channel.UNLIMITED)

        given(callRepository)
            .function(callRepository::ongoingCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(flowOf(conversations))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(groupConversation.id))
            .thenReturn(groupConversationUpdates.asFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(oneOnOneConversation.id))
            .thenReturn(oneOnOneDetailsChannel.consumeAsFlow())

        observeConversationsUseCase().test {
            oneOnOneDetailsChannel.send(firstOneOnOneDetails)

            val detailList: List<ConversationDetails> = awaitItem()
            assertContentEquals(groupConversationUpdates, detailList)

            oneOnOneDetailsChannel.send(secondOneOnOneDetails)
            val updatedDetailList: List<ConversationDetails> = awaitItem()
            assertContentEquals(groupConversationUpdates + firstOneOnOneDetails, updatedDetailList)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAConversationIsAddedToTheList_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        val groupConversation = TestConversation.GROUP()
        val groupConversationDetails = ConversationDetails.Group(groupConversation, LegalHoldStatus.DISABLED)

        val selfConversation = TestConversation.SELF
        val selfConversationDetails = ConversationDetails.Self(selfConversation)

        val firstConversationsList = listOf(groupConversation)
        val secondConversationsList = firstConversationsList + selfConversation
        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        given(callRepository)
            .function(callRepository::ongoingCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(conversationListUpdates.consumeAsFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(groupConversation.id))
            .thenReturn(flowOf(groupConversationDetails))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(selfConversation.id))
            .thenReturn(flowOf(selfConversationDetails))

        observeConversationsUseCase().test {
            assertContentEquals(listOf(groupConversationDetails), awaitItem())

            conversationListUpdates.close()
            awaitComplete()
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAnOngoingCall_whenFetchingConversationDetails_thenTheConversationShouldHaveAnOngoingCall() = runTest {
        val groupConversation = TestConversation.GROUP()
        val groupConversationDetails = ConversationDetails.Group(groupConversation, LegalHoldStatus.DISABLED)

        val ongoingCall = Call(
            conversationId = groupConversation.id,
            status = CallStatus.STILL_ONGOING,
            isMuted = false,
            isCameraOn = false,
            callerId = "anotherUserId",
            conversationName = groupConversation.name,
            conversationType = Conversation.Type.GROUP,
            callerName = "otherUserName",
            callerTeamName = null
        )

        val firstConversationsList = listOf(groupConversation)

        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        given(callRepository)
            .function(callRepository::ongoingCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf(ongoingCall)))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(conversationListUpdates.consumeAsFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(groupConversation.id))
            .thenReturn(flowOf(groupConversationDetails))

        observeConversationsUseCase().test {
            assertEquals(true, (awaitItem()[0] as ConversationDetails.Group).hasOngoingCall)
        }
    }

    @Test
    fun givenAConversationWithoutAnOngoingCall_whenFetchingConversationDetails_thenTheConversationShouldNotHaveAnOngoingCall() = runTest {
        val groupConversation = TestConversation.GROUP()
        val groupConversationDetails = ConversationDetails.Group(groupConversation, LegalHoldStatus.DISABLED)

        val firstConversationsList = listOf(groupConversation)

        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        given(callRepository)
            .function(callRepository::ongoingCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationList)
            .whenInvoked()
            .thenReturn(conversationListUpdates.consumeAsFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(groupConversation.id))
            .thenReturn(flowOf(groupConversationDetails))

        observeConversationsUseCase().test {
            assertEquals(false, (awaitItem()[0] as ConversationDetails.Group).hasOngoingCall)
        }
    }
}
