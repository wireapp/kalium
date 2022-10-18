package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinExistingMLSConversationsUseCaseTest {

    @Test
    fun givenMLSSupportIsDisabled_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(false)
                .withGetConversationsByGroupStateSuccessful()
                .withRequestToJoinMLSGroupSuccessful()
                .arrange()

            joinExistingMLSConversationsUseCase().shouldSucceed()

            verify(arrangement.conversationGroupRepository)
                .suspendFunction(arrangement.conversationGroupRepository::requestToJoinMLSGroup)
                .with(eq(Arrangement.MLS_CONVERSATION1))
                .wasNotInvoked()
        }

    @Test
    fun givenExistingConversations_whenInvokingUseCase_ThenRequestToJoinConversationIsCalledForAllConversations() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withGetConversationsByGroupStateSuccessful()
                .withRequestToJoinMLSGroupSuccessful()
                .arrange()

            joinExistingMLSConversationsUseCase().shouldSucceed()

            verify(arrangement.conversationGroupRepository)
                .suspendFunction(arrangement.conversationGroupRepository::requestToJoinMLSGroup)
                .with(eq(Arrangement.MLS_CONVERSATION1))
                .wasInvoked(once)

            verify(arrangement.conversationGroupRepository)
                .suspendFunction(arrangement.conversationGroupRepository::requestToJoinMLSGroup)
                .with(eq(Arrangement.MLS_CONVERSATION2))
                .wasInvoked(once)
        }

    @Test
    fun givenOutOfDateEpochFailure_whenInvokingUseCase_ThenRetryWithNewEpoch() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withGetConversationsByGroupStateSuccessful(conversations = listOf(Arrangement.MLS_CONVERSATION1))
            .withRequestToJoinMLSGroupSuccessful()
            .withRequestToJoinMLSGroupFailing(Arrangement.MLS_STALE_MESSAGE_FAILURE, times = 1)
            .withFetchConversationSuccessful()
            .withGetConversationByIdSuccessful()
            .arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(Arrangement.MLS_CONVERSATION1.id))
            .wasInvoked(once)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::requestToJoinMLSGroup)
            .with(eq(Arrangement.MLS_CONVERSATION1))
            .wasInvoked(twice)

    }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_ThenFailureIsReported() = runTest {
        val (_, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withGetConversationsByGroupStateSuccessful()
            .withRequestToJoinMLSGroupFailing(Arrangement.MLS_UNSUPPORTED_PROPOSAL_FAILURE)
            .arrange()

        joinExistingMLSConversationsUseCase().shouldFail()
    }

    private class Arrangement {

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

        fun arrange() = this to JoinExistingMLSConversationsUseCase(featureSupport, conversationRepository, conversationGroupRepository)

        @Suppress("MaxLineLength")
        fun withGetConversationsByGroupStateSuccessful(conversations: List<Conversation> = listOf(MLS_CONVERSATION1, MLS_CONVERSATION2)) =
            apply {
                given(conversationRepository)
                    .suspendFunction(conversationRepository::getConversationsByGroupState)
                    .whenInvokedWith(anything())
                    .then { Either.Right(conversations) }
            }

        fun withFetchConversationSuccessful() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(anything())
                .then { Either.Right(Unit) }
        }

        fun withGetConversationByIdSuccessful() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::detailsById)
                .whenInvokedWith(anything())
                .then { Either.Right(MLS_CONVERSATION1) }
        }

        fun withRequestToJoinMLSGroupSuccessful() = apply {
            given(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::requestToJoinMLSGroup)
                .whenInvokedWith(anything())
                .then { Either.Right(Unit) }
        }

        fun withRequestToJoinMLSGroupFailing(failure: CoreFailure, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            given(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::requestToJoinMLSGroup)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times })
                .then { Either.Left(failure) }
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        companion object {
            val MLS_UNSUPPORTED_PROPOSAL_FAILURE = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
                        422,
                        "Unsupported proposal type",
                        "mls-unsupported-proposal"
                    )
                )
            )

            val MLS_STALE_MESSAGE_FAILURE = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
                        403,
                        "The conversation epoch in a message is too old",
                        "mls-stale-message"
                    )
                )
            )

            val MLS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GroupID("group1"),
                    Conversation.ProtocolInfo.MLS.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = Clock.System.now(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id1", "domain"))

            val MLS_CONVERSATION2 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GroupID("group1"),
                    Conversation.ProtocolInfo.MLS.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = Clock.System.now(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id2", "domain"))
        }
    }
}
