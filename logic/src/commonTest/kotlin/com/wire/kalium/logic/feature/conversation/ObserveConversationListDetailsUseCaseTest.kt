package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Suppress("LongMethod")
class ObserveConversationListDetailsUseCaseTest {

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationListShouldBeCalled() = runTest {
        // Given
        val groupConversation = TestConversation.GROUP()
        val selfConversation = TestConversation.SELF
        val conversations = listOf(selfConversation, groupConversation)
        val selfConversationDetails = ConversationDetails.Self(selfConversation)
        val groupConversationDetails =
            ConversationDetails.Group(
                groupConversation,
                LegalHoldStatus.DISABLED,
                unreadMessagesCount = 0,
                lastUnreadMessage = null
            )

        val (arrangement, observeConversationsUseCase) = Arrangement()
            .withOngoingCalls(listOf())
            .withConversationsList(conversations)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .withSuccessfulConversationsDetailsListUpdates(selfConversation, listOf(selfConversationDetails))
            .withUnreadConversationCount(0L)
            .withIsSelfUserMember(true)
            .arrange()

        // When

        observeConversationsUseCase().collect()

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationList)
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationDetailsShouldBeCalledForEachID() = runTest {
        val selfConversation = TestConversation.SELF
        val groupConversation = TestConversation.GROUP()
        val conversations = listOf(selfConversation, groupConversation)

        val selfConversationDetails = ConversationDetails.Self(selfConversation)
        val groupConversationDetails = ConversationDetails.Group(
            conversation = groupConversation,
            legalHoldStatus = LegalHoldStatus.DISABLED,
            unreadMessagesCount = 0,
            lastUnreadMessage = null
        )

        val (arrangement, observeConversationsUseCase) = Arrangement()
            .withOngoingCalls(listOf())
            .withConversationsList(conversations)
            .withSuccessfulConversationsDetailsListUpdates(selfConversation, listOf(selfConversationDetails))
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .withUnreadConversationCount(0L)
            .withIsSelfUserMember(true)
            .arrange()

        observeConversationsUseCase().collect()

        with(arrangement) {
            conversations.forEach { conversation ->
                verify(conversationRepository)
                    .suspendFunction(conversationRepository::observeConversationDetailsById)
                    .with(eq(conversation.id))
                    .wasInvoked(exactly = once)
            }
        }
    }

    @Test
    fun givenSomeConversationsDetailsAreUpdated_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        val oneOnOneConversation = TestConversation.ONE_ON_ONE
        val groupConversation = TestConversation.GROUP()
        val conversations = listOf(groupConversation, oneOnOneConversation)

        val groupConversationUpdates = listOf(
            ConversationDetails.Group(
                groupConversation,
                LegalHoldStatus.DISABLED,
                unreadMessagesCount = 0,
                lastUnreadMessage = null
            )
        )

        val firstOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER,
            ConnectionState.ACCEPTED,
            LegalHoldStatus.ENABLED,
            UserType.INTERNAL,
            unreadMessagesCount = 0,
            lastUnreadMessage = null
        )
        val secondOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER.copy(name = "New User Name"),
            ConnectionState.PENDING,
            LegalHoldStatus.DISABLED,
            UserType.INTERNAL,
            unreadMessagesCount = 0,
            lastUnreadMessage = null
        )

        val oneOnOneDetailsChannel = Channel<ConversationDetails.OneOne>(Channel.UNLIMITED)

        val (_, observeConversationsUseCase) = Arrangement()
            .withOngoingCalls(listOf())
            .withConversationsList(conversations)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, groupConversationUpdates)
            .withConversationsDetailsChannelUpdates(oneOnOneConversation, oneOnOneDetailsChannel)
            .withUnreadConversationCount(0L)
            .withIsSelfUserMember(true)
            .arrange()

        observeConversationsUseCase().test {
            oneOnOneDetailsChannel.send(firstOneOnOneDetails)

            val conversationList: ConversationListDetails = awaitItem()
            assertContentEquals(groupConversationUpdates + firstOneOnOneDetails, conversationList.conversationList)

            oneOnOneDetailsChannel.send(secondOneOnOneDetails)
            val updatedConversationList: ConversationListDetails = awaitItem()
            assertContentEquals(groupConversationUpdates + secondOneOnOneDetails, updatedConversationList.conversationList)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAConversationIsAddedToTheList_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        val groupConversation = TestConversation.GROUP()
        val groupConversationDetails = ConversationDetails.Group(
            groupConversation,
            LegalHoldStatus.DISABLED,
            unreadMessagesCount = 0,
            lastUnreadMessage = null
        )

        val selfConversation = TestConversation.SELF
        val selfConversationDetails = ConversationDetails.Self(selfConversation)

        val firstConversationsList = listOf(groupConversation)
        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        val (_, observeConversationsUseCase) = Arrangement()
            .withOngoingCalls(listOf())
            .withConversationsList(conversationListUpdates)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .withSuccessfulConversationsDetailsListUpdates(selfConversation, listOf(selfConversationDetails))
            .withUnreadConversationCount(0L)
            .withIsSelfUserMember(true)
            .arrange()

        observeConversationsUseCase().test {
            assertContentEquals(listOf(groupConversationDetails), awaitItem().conversationList)

            conversationListUpdates.close()
            awaitComplete()
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAnOngoingCall_whenFetchingConversationDetails_thenTheConversationShouldHaveAnOngoingCall() = runTest {
        val groupConversation = TestConversation.GROUP()
        val groupConversationDetails = ConversationDetails.Group(
            groupConversation,
            LegalHoldStatus.DISABLED,
            unreadMessagesCount = 0,
            lastUnreadMessage = null
        )

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

        val (_, observeConversationsUseCase) = Arrangement()
            .withOngoingCalls(listOf(ongoingCall))
            .withConversationsList(conversationListUpdates)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .withUnreadConversationCount(0L)
            .withIsSelfUserMember(true)
            .arrange()

        observeConversationsUseCase().test {
            assertEquals(true, (awaitItem().conversationList[0] as ConversationDetails.Group).hasOngoingCall)
        }
    }

    @Test
    fun givenAConversationWithoutAnOngoingCall_whenFetchingConversationDetails_thenTheConversationShouldNotHaveAnOngoingCall() = runTest {
        val groupConversation = TestConversation.GROUP()

        val groupConversationDetails = ConversationDetails.Group(
            groupConversation,
            LegalHoldStatus.DISABLED,
            unreadMessagesCount = 0,
            lastUnreadMessage = null
        )

        val firstConversationsList = listOf(groupConversation)

        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        val (_, observeConversationsUseCase) = Arrangement()
            .withOngoingCalls(listOf())
            .withConversationsList(conversationListUpdates)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .withUnreadConversationCount(0L)
            .withIsSelfUserMember(true)
            .arrange()

        observeConversationsUseCase().test {
            assertEquals(false, (awaitItem().conversationList[0] as ConversationDetails.Group).hasOngoingCall)
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenConversationDetailsFailure_whenObservingDetailsList_thenIgnoreConversationWithFailure() = runTest {
        val successConversation = TestConversation.ONE_ON_ONE.copy(id = ConversationId("successId", "domain"))
        val successConversationDetails = TestConversationDetails.CONVERSATION_ONE_ONE.copy(conversation = successConversation)
        val failureConversation = TestConversation.ONE_ON_ONE.copy(id = ConversationId("failedId", "domain"))

        val (_, observeConversationsUseCase) = Arrangement()
            .withOngoingCalls(listOf())
            .withConversationsList(listOf(successConversation, failureConversation))
            .withSuccessfulConversationsDetailsListUpdates(successConversation, listOf(successConversationDetails))
            .withErrorConversationsDetailsListUpdates(failureConversation)
            .withUnreadConversationCount(0L)
            .withIsSelfUserMember(true)
            .arrange()

        observeConversationsUseCase().test {
            assertEquals(awaitItem().conversationList, listOf(successConversationDetails))
            awaitComplete()
        }
    }

    private class Arrangement {

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        @Mock
        val callRepository: CallRepository = mock(CallRepository::class)

        @Mock
        val observeIsSelfUserMember: ObserveIsSelfUserMemberUseCase = mock(ObserveIsSelfUserMemberUseCase::class)

        fun withIsSelfUserMember(isMember: Boolean) = apply {
            given(observeIsSelfUserMember)
                .suspendFunction(observeIsSelfUserMember::invoke)
                .whenInvokedWith(anything())
                .thenReturn(flowOf(IsSelfUserMemberResult.Success(isMember)))
        }

        fun withUnreadConversationCount(count: Long) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getUnreadConversationCount)
                .whenInvoked()
                .thenReturn(Either.Right(count))
        }

        fun withConversationsDetailsChannelUpdates(
            conversation: Conversation,
            expectedConversationDetails: Channel<ConversationDetails.OneOne>
        ) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(eq(conversation.id))
                .thenReturn(expectedConversationDetails.consumeAsFlow().map { Either.Right(it) })
        }

        fun withSuccessfulConversationsDetailsListUpdates(
            conversation: Conversation,
            expectedConversationDetailsList: List<ConversationDetails>
        ) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(eq(conversation.id))
                .thenReturn(expectedConversationDetailsList.asFlow().map { Either.Right(it) })
        }

        fun withErrorConversationsDetailsListUpdates(conversation: Conversation) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(eq(conversation.id))
                .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withConversationsList(conversations: List<Conversation>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationList)
                .whenInvoked()
                .thenReturn(flowOf(conversations))
        }

        fun withConversationsList(conversations: Channel<List<Conversation>>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationList)
                .whenInvoked()
                .thenReturn(conversations.consumeAsFlow())
        }

        fun withOngoingCalls(callsList: List<Call>) = apply {
            given(callRepository).suspendFunction(callRepository::ongoingCallsFlow)
                .whenInvoked()
                .thenReturn(flowOf(callsList))
        }

        fun arrange() = this to ObserveConversationListDetailsUseCaseImpl(conversationRepository, callRepository, observeIsSelfUserMember)
    }

}
