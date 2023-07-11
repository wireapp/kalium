package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationVerificationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetConversationMLSVerificationStatusUseCaseTest {
    @Test
    fun givenMLSSupportIsDisabled_whenInvokingUseCase_thenGetConversationMLSVerificationStatusIsNotCalled() =
        runTest {
            val (arrangement, getConversationMLSVerificationStatus) = Arrangement()
                .withIsMLSSupported(false)
                .withGetConversationsByIdSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            assertEquals(
                ConversationVerificationStatusResult.Success(ConversationProtocol.PROTEUS, ConversationVerificationStatus.NOT_VERIFIED),
                getConversationMLSVerificationStatus(Arrangement.MLS_CONVERSATION1.id)
            )

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::getConversationVerificationStatus)
                .with(eq(Arrangement.MLS_CONVERSATION1))
                .wasNotInvoked()
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenInvokingUseCase_thenGetConversationMLSVerificationStatusIsNotCalled() =
        runTest {
            val (arrangement, getConversationMLSVerificationStatus) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withGetConversationsByIdSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            assertEquals(
                ConversationVerificationStatusResult.Success(ConversationProtocol.PROTEUS, ConversationVerificationStatus.NOT_VERIFIED),
                getConversationMLSVerificationStatus(Arrangement.MLS_CONVERSATION1.id)
            )

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::getConversationVerificationStatus)
                .with(eq(Arrangement.MLS_CONVERSATION1))
                .wasNotInvoked()
        }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_thenGetConversationMLSVerificationStatusIsVerified() = runTest {
        val (arrangement, getConversationMLSVerificationStatus) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByIdSuccessful()
            .withMLSGroupVerificationStatus(ConversationVerificationStatus.VERIFIED)
            .arrange()

        assertEquals(
            ConversationVerificationStatusResult.Success(ConversationProtocol.MLS, ConversationVerificationStatus.VERIFIED),
            getConversationMLSVerificationStatus(Arrangement.MLS_CONVERSATION1.id)
        )
    }

    @Test
    fun givenNonRecoverableFailureAndNotVerifiedMLSStatus_whenInvokingUseCase_thenGetConversationMLSVerificationStatusIsVerified() =
        runTest {
            val (arrangement, getConversationMLSVerificationStatus) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByIdSuccessful()
                .withMLSGroupVerificationStatus(ConversationVerificationStatus.NOT_VERIFIED)
                .arrange()

            assertEquals(
                ConversationVerificationStatusResult.Success(ConversationProtocol.MLS, ConversationVerificationStatus.NOT_VERIFIED),
                getConversationMLSVerificationStatus(Arrangement.MLS_CONVERSATION1.id)
            )
        }

    private class Arrangement {

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        fun arrange() = this to GetConversationVerificationStatusUseCaseImpl(
            featureSupport,
            clientRepository,
            conversationRepository,
            mlsConversationRepository
        )

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

        fun withJoinByExternalCommitSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::joinGroupByExternalCommit)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun withHasRegisteredMLSClient(result: Boolean) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(Either.Right(result))
        }

        companion object {
            val GROUP_ID1 = GroupID("group1")

            val MLS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID1,
                    Conversation.ProtocolInfo.MLS.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id1", "domain"))
        }
    }
}
