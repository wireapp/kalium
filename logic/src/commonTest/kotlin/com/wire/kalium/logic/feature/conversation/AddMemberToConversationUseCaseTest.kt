package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProtocolInfo
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
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMemberToConversationUseCaseTest {

    @Test
    fun givenMemberAndProteusConversation_WhenAddMemberIsSuccessful_ThenMemberIsAddedToDB() = runTest {
        val (arrangement, addMemberUseCase) = Arrangement()
            .withConversationProtocolIs(Arrangement.proteusProtocolInfo)
            .withAddMemberToProteusGroupSuccessful()
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

        private val addMemberUseCase = AddMemberToConversationUseCaseImpl(
            conversationRepository,
            mlsConversationRepository
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

        fun arrange() = this to addMemberUseCase

        companion object {
            const val mlsGroupId = "mlsGroupId"
            val proteusProtocolInfo = ProtocolInfo.Proteus
            val mlsProtocolInfo = ProtocolInfo.MLS(mlsGroupId, groupState = ProtocolInfo.MLS.GroupState.ESTABLISHED)

        }
    }

}
