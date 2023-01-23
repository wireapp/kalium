package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GetIncomingCallsUseCaseTest {

    @Test
    fun givenAnEmptyCallList_whenInvokingGetIncomingCallsUseCase_thenEmitsAnEmptyListOfCalls() = runTest {
        val (_, getIncomingCalls) = Arrangement()
            .withIncomingCalls(listOf())
            .withSelfUserStatus(UserAvailabilityStatus.AVAILABLE)
            .arrange()

        getIncomingCalls().test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun givenNotEmptyCallList_whenInvokingGetIncomingCallsUseCase_thenNonEmptyNotificationList() = runTest {
        val (_, getIncomingCalls) = Arrangement()
            .withSelfUserStatus(UserAvailabilityStatus.AVAILABLE)
            .withConversationDetails { id -> Either.Right(conversationWithMuteStatus(id, MutedConversationStatus.AllAllowed)) }
            .withIncomingCalls(
                listOf<Call>(incomingCall(0), incomingCall(1))
            )
            .arrange()

        getIncomingCalls().test {
            val firstItem = awaitItem()
            assertEquals(2, firstItem.size)
            assertEquals(TestConversation.id(0), firstItem.first().conversationId)
        }
    }

    @Test
    fun givenUserWithAwayStatus_whenIncomingCallComes_thenNoCallsPropagated() = runTest {
        val (_, getIncomingCalls) = Arrangement()
            .withSelfUserStatus(UserAvailabilityStatus.AWAY)
            .withConversationDetails { id -> Either.Right(conversationWithMuteStatus(id, MutedConversationStatus.AllAllowed)) }
            .withIncomingCalls(
                listOf<Call>(incomingCall(0), incomingCall(1))
            )
            .arrange()

        getIncomingCalls().test {
            val firstItem = awaitItem()
            assertTrue(firstItem.isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenUserWithBusyStatus_whenIncomingCallComes_thenCallsPropagated() = runTest {
        val (_, getIncomingCalls) = Arrangement()
            .withSelfUserStatus(UserAvailabilityStatus.BUSY)
            .withConversationDetails { id -> Either.Right(conversationWithMuteStatus(id, MutedConversationStatus.AllAllowed)) }
            .withIncomingCalls(
                listOf<Call>(incomingCall(0), incomingCall(1))
            )
            .arrange()

        getIncomingCalls().test {
            val firstItem = awaitItem()
            assertEquals(2, firstItem.size)
            assertEquals(TestConversation.id(0), firstItem[0].conversationId)
            assertEquals(TestConversation.id(1), firstItem[1].conversationId)
        }
    }

    @Test
    fun givenMutedConversation_whenIncomingCallComesInThatConversation_thenCallIsNotPropagated() = runTest {
        val (_, getIncomingCalls) = Arrangement()
            .withSelfUserStatus(UserAvailabilityStatus.AVAILABLE)
            .withConversationDetails { id ->
                if (id == TestConversation.id(0))
                    Either.Right(conversationWithMuteStatus(id, MutedConversationStatus.AllMuted))
                else
                    Either.Right(conversationWithMuteStatus(id, MutedConversationStatus.AllAllowed))
            }
            .withIncomingCalls(
                listOf<Call>(incomingCall(0), incomingCall(1))
            )
            .arrange()

        getIncomingCalls().test {
            val firstItem = awaitItem()
            assertEquals(1, firstItem.size)
            assertEquals(TestConversation.id(1), firstItem[0].conversationId)
        }
    }

    @Test
    fun givenOnlyMentionsAllowedInConversation_whenIncomingCallComesInThatConversation_thenCallIsNotPropagated() = runTest {
        val (_, getIncomingCalls) = Arrangement()
            .withSelfUserStatus(UserAvailabilityStatus.AVAILABLE)
            .withConversationDetails { id ->
                Either.Right(conversationWithMuteStatus(id, MutedConversationStatus.OnlyMentionsAndRepliesAllowed))
            }
            .withIncomingCalls(
                listOf<Call>(incomingCall(0), incomingCall(1))
            )
            .arrange()

        getIncomingCalls().test {
            val firstItem = awaitItem()
            assertEquals(2, firstItem.size)
            assertEquals(TestConversation.id(0), firstItem[0].conversationId)
            assertEquals(TestConversation.id(1), firstItem[1].conversationId)
        }
    }

    @Test
    fun givenNoConversationDetails_whenIncomingCallComesInThatConversation_thenCallIsNotPropagated() = runTest {
        val (_, getIncomingCalls) = Arrangement()
            .withSelfUserStatus(UserAvailabilityStatus.AVAILABLE)
            .withConversationDetails { id ->
                if (id == TestConversation.id(0))
                    Either.Left(StorageFailure.DataNotFound)
                else
                    Either.Right(conversationWithMuteStatus(id, MutedConversationStatus.AllAllowed))
            }
            .withIncomingCalls(
                listOf<Call>(incomingCall(0), incomingCall(1))
            )
            .arrange()

        getIncomingCalls().test {
            val firstItem = awaitItem()
            assertEquals(1, firstItem.size)
            assertEquals(TestConversation.id(1), firstItem[0].conversationId)
        }
    }

    private class Arrangement {

        @Mock
        val userRepository: UserRepository = mock(classOf<UserRepository>())

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val callRepository: CallRepository = mock(classOf<CallRepository>())

        val getIncomingCallsUseCase: GetIncomingCallsUseCase = GetIncomingCallsUseCaseImpl(
            userRepository = userRepository,
            conversationRepository = conversationRepository,
            callRepository = callRepository
        )

        fun withIncomingCalls(calls: List<Call>): Arrangement {
            given(callRepository)
                .suspendFunction(callRepository::incomingCallsFlow)
                .whenInvoked()
                .then { MutableStateFlow(calls) }

            return this
        }

        fun withSelfUserStatus(status: UserAvailabilityStatus): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .then { flowOf(selfUserWithStatus(status)) }

            return this
        }

        fun withConversationDetails(detailsGetter: (ConversationId) -> Either<StorageFailure, Conversation>): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::baseInfoById)
                .whenInvokedWith(any())
                .then { id -> detailsGetter(id) }
            return this
        }

        fun arrange() = this to getIncomingCallsUseCase
    }

    companion object {
        private fun selfUserWithStatus(status: UserAvailabilityStatus = UserAvailabilityStatus.NONE) =
            TestUser.SELF.copy(availabilityStatus = status)

        private fun conversationWithMuteStatus(id: ConversationId, status: MutedConversationStatus) =
            TestConversation.one_on_one(id).copy(mutedStatus = status)

        private fun incomingCall(conversationIdSuffix: Int = 0) =
            TestCall.oneOnOneIncomingCall(TestConversation.id(conversationIdSuffix))
    }
}
