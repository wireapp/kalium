package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMemberToConversationUseCaseTest {

    @Test
    fun givenMemberAndProteusConversation_WhenAddMemberIsSuccessful_ThenMemberIsAddedToDB() = runTest {
        val (arrangement, addMemberUseCase) = Arrangement()
            .withConversationProtocolIs(Arrangement.proteusProtocolInfo)
            .withAddMemberToProteusGroupSuccessful()
            .withPersistMessage(Either.Right(Unit))
            .arrange()

        addMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))

        // VERIFY PROTEUS INVOKED CORRECTLY
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::addMembers)
            .with(eq(listOf(TestConversation.USER_1)), eq(TestConversation.ID))
            .wasInvoked(exactly = once)

        // VERIFY MLS NOT INVOKED
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMemberAndMLSConversation_WhenAddMemberIsSuccessful_ThenMemberIsAddedToDB() = runTest {
        val (arrangement, addMemberUseCase) = Arrangement()
            .withConversationProtocolIs(Arrangement.mlsProtocolInfo)
            .withAddMemberToMLSGroupSuccessful()
            .withPersistMessage(Either.Right(Unit))
            .arrange()

        addMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))

        // VERIFY PROTEUS FUNCTION NOT INVOKED
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::addMembers)
            .with(any(), any())
            .wasNotInvoked()

        // VERIFY MLS FUNCTIONS INVOKED CORRECTLY
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(Arrangement.mlsGroupId), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        var selfUserId = UserId("my-own-user-id", "my-domain")

        private val addMemberUseCase = AddMemberToConversationUseCaseImpl(
            conversationRepository,
            mlsConversationRepository,
            selfUserId,
            persistMessage
        )

        fun withAddMemberToProteusGroupSuccessful() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::addMembers)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withAddMemberToMLSGroupSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::addMemberToMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withConversationProtocolIs(protocolInfo: ProtocolInfo) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::detailsById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(TestConversation.GROUP(protocolInfo)))
        }

        fun withPersistMessage(either: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(either)
        }

        fun arrange() = this to addMemberUseCase

        companion object {
            val mlsGroupId = GroupID("mlsGroupId")
            val proteusProtocolInfo = ProtocolInfo.Proteus
            val mlsProtocolInfo = ProtocolInfo.MLS(
                mlsGroupId,
                groupState = ProtocolInfo.MLS.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )

        }
    }

}
