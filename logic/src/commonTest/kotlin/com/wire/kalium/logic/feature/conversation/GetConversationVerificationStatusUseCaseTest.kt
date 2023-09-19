package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationVerificationStatus
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetConversationVerificationStatusUseCaseTest {

    @Test
    fun givenNoMLSClientIsRegistered_whenInvokingUseCase_thengetConversationVerificationStatusIsNotCalled() =
        runTest {
            val (arrangement, getConversationVerificationStatus) = Arrangement()
                .withGetConversationsByIdSuccessful(Arrangement.PROTEUS_CONVERSATION1)
                .arrange()

            assertEquals(
                ConversationVerificationStatusResult.Success(ConversationProtocol.PROTEUS, ConversationVerificationStatus.NOT_VERIFIED),
                getConversationVerificationStatus(Arrangement.PROTEUS_CONVERSATION1.id)
            )

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::getConversationVerificationStatus)
                .with(eq(Arrangement.PROTEUS_CONVERSATION1))
                .wasNotInvoked()

            verify(arrangement.verificationStatusHandler)
                .suspendFunction(arrangement.verificationStatusHandler::invoke)
                .with(eq(Arrangement.PROTEUS_CONVERSATION1), eq(ConversationVerificationStatus.NOT_VERIFIED))
                .wasInvoked(once)
        }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_thenGetConversationVerificationStatusIsVerified() = runTest {
        val (arrangement, getConversationVerificationStatus) = Arrangement()
            .withGetConversationsByIdSuccessful()
            .withMLSGroupVerificationStatus(ConversationVerificationStatus.VERIFIED)
            .arrange()

        assertEquals(
            ConversationVerificationStatusResult.Success(ConversationProtocol.MLS, ConversationVerificationStatus.VERIFIED),
            getConversationVerificationStatus(Arrangement.MLS_CONVERSATION1.id)
        )

        verify(arrangement.verificationStatusHandler)
            .suspendFunction(arrangement.verificationStatusHandler::invoke)
            .with(eq(Arrangement.MLS_CONVERSATION1), eq(ConversationVerificationStatus.VERIFIED))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableFailureAndNotVerifiedMLSStatus_whenInvokingUseCase_thenGetConversationVerificationStatusIsVerified() =
        runTest {
            val (arrangement, getConversationVerificationStatus) = Arrangement()
                .withGetConversationsByIdSuccessful()
                .withMLSGroupVerificationStatus(ConversationVerificationStatus.NOT_VERIFIED)
                .arrange()

            assertEquals(
                ConversationVerificationStatusResult.Success(ConversationProtocol.MLS, ConversationVerificationStatus.NOT_VERIFIED),
                getConversationVerificationStatus(Arrangement.MLS_CONVERSATION1.id)
            )

            verify(arrangement.verificationStatusHandler)
                .suspendFunction(arrangement.verificationStatusHandler::invoke)
                .with(eq(Arrangement.MLS_CONVERSATION1), eq(ConversationVerificationStatus.NOT_VERIFIED))
                .wasInvoked(once)
        }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val verificationStatusHandler = mock(classOf<ConversationVerificationStatusHandler>())

        fun arrange() = this to GetConversationVerificationStatusUseCaseImpl(
            conversationRepository,
            mlsConversationRepository,
            verificationStatusHandler
        )

        init {
            given(verificationStatusHandler)
                .suspendFunction(verificationStatusHandler::invoke)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        @Suppress("MaxLineLength")
        fun withGetConversationsByIdSuccessful(conversation: Conversation = MLS_CONVERSATION1) =
            apply {
                given(conversationRepository)
                    .suspendFunction(conversationRepository::baseInfoById)
                    .whenInvokedWith(anything())
                    .then { Either.Right(conversation) }
            }

        fun withMLSGroupVerificationStatus(status: ConversationVerificationStatus) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::getConversationVerificationStatus)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(status))
        }

        companion object {
            val GROUP_ID1 = GroupID("group1")

            val MLS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID1,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    Instant.DISTANT_PAST,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id1", "domain"))

            val PROTEUS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.Proteus
            ).copy(id = ConversationId("id1", "domain"))
        }
    }
}
