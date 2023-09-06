package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveConversationProtocolInfoUseCaseTest {

    @Test
    fun givenNoMLSClientIsRegistered_whenInvokingUseCase_thengetConversationVerificationStatusIsNotCalled() =
        runTest {
            val conversation = Arrangement.PROTEUS_CONVERSATION1
            val (arrangement, getConversationVerificationStatus) = Arrangement()
                .withGetConversationsByIdSuccessful(conversation.protocol)
                .arrange()

            getConversationVerificationStatus(conversation.id).test {
                assertEquals(
                    ConversationVerificationStatusResult.Success(conversation.protocol),
                    awaitItem()
                )
                awaitComplete()
            }

            verify(arrangement.verificationStatusHandler)
                .suspendFunction(arrangement.verificationStatusHandler::invoke)
                .with(eq(conversation.id))
                .wasInvoked(once)
        }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_thenGetConversationVerificationStatusIsVerified() = runTest {
        val conversation = Arrangement.MLS_CONVERSATION1
        val (arrangement, getConversationVerificationStatus) = Arrangement()
            .withGetConversationsByIdSuccessful()
            .arrange()

        getConversationVerificationStatus(conversation.id).test {
            assertEquals(
                ConversationVerificationStatusResult.Success(conversation.protocol),
                awaitItem()
            )
            awaitComplete()
        }

        verify(arrangement.verificationStatusHandler)
            .suspendFunction(arrangement.verificationStatusHandler::invoke)
            .with(eq(conversation.id))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableFailureAndNotVerifiedMLSStatus_whenInvokingUseCase_thenGetConversationVerificationStatusIsVerified() =
        runTest {
            val mlsConversation = Arrangement.MLS_CONVERSATION1
            val (arrangement, getConversationVerificationStatus) = Arrangement()
                .withGetConversationsByIdSuccessful()
                .arrange()

            getConversationVerificationStatus(mlsConversation.id).test {
                assertEquals(
                    ConversationVerificationStatusResult.Success(mlsConversation.protocol),
                    awaitItem()
                )
                awaitComplete()
            }

            verify(arrangement.verificationStatusHandler)
                .suspendFunction(arrangement.verificationStatusHandler::invoke)
                .with(eq(mlsConversation.id))
                .wasInvoked(once)
        }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val verificationStatusHandler = mock(classOf<ConversationVerificationStatusHandler>())

        fun arrange() = this to ObserveConversationProtocolInfoUseCaseImpl(
            conversationRepository,
            verificationStatusHandler
        )

        init {
            given(verificationStatusHandler)
                .suspendFunction(verificationStatusHandler::invoke)
                .whenInvokedWith(any())
                .thenReturn(flowOf())
        }

        @Suppress("MaxLineLength")
        fun withGetConversationsByIdSuccessful(protocol: Conversation.ProtocolInfo = MLS_CONVERSATION1.protocol) =
            apply {
                given(conversationRepository)
                    .suspendFunction(conversationRepository::observeConversationProtocolInfo)
                    .whenInvokedWith(anything())
                    .then { flowOf(Either.Right(protocol))  }
            }

        companion object {
            val GROUP_ID1 = GroupID("group1")

            val MLS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID1,
                    Conversation.ProtocolInfo.MLS.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                    verificationStatus = Conversation.VerificationStatus.NOT_VERIFIED
                )
            ).copy(id = ConversationId("id1", "domain"))

            val PROTEUS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.Proteus(Conversation.VerificationStatus.NOT_VERIFIED)
            ).copy(id = ConversationId("id1", "domain"))
        }
    }
}
