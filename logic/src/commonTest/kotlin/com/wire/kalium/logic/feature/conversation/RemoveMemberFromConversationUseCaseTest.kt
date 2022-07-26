package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.framework.TestClient
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
class RemoveMemberFromConversationUseCaseTest {

    @Test
    fun givenMemberAndProteusConversation_WhenRemoveMemberIsSuccessful_ThenMemberIsRemovedFromDB() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withConversationProtocolIs(Arrangement.proteusProtocolInfo)
            .withRemoveMemberFromProteusGroupSuccessful()
            .arrange()

        removeMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))

        // VERIFY PROTEUS INVOKED CORRECTLY
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteMembers)
            .with(eq(listOf(TestConversation.MEMBER_TEST1)), eq(TestConversation.ID))
            .wasInvoked(exactly = once)

        // VERIFY MLS NOT INVOKED
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMemberAndProteusConversation_WhenRemoveMemberFailed_ThenFunctionsInvokedCorrectly() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withConversationProtocolIs(Arrangement.proteusProtocolInfo)
            .withRemoveMemberFromProteusGroupFailed()
            .arrange()

        removeMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))

        // VERIFY PROTEUS INVOKED CORRECTLY
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteMembers)
            .with(any(), any())
            .wasNotInvoked()

        // VERIFY MLS NOT INVOKED
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMemberAndMLSConversation_WhenRemoveMemberIsSuccessful_ThenMemberIsRemovedFromDB() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withConversationProtocolIs(Arrangement.mlsProtocolInfo)
            .withRemoveMemberFromMLSGroupSuccessful()
            .withExistingSelfClientId()
            .arrange()

        removeMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))

        // VERIFY PROTEUS FUNCTION NOT INVOKED
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteMembers)
            .with(any(), any())
            .wasNotInvoked()

        // VERIFY MLS FUNCTIONS INVOKED CORRECTLY
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(eq(TestClient.CLIENT_ID), eq(Arrangement.mlsGroupId), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        private val removeMemberUseCase = RemoveMemberFromConversationUseCaseImpl(
            conversationRepository,
            mlsConversationRepository,
            clientRepository
        )

        fun withExistingSelfClientId() = apply {
            given(clientRepository).suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .then { Either.Right(TestClient.CLIENT_ID) }
        }

        fun withRemoveMemberFromProteusGroupSuccessful() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteMembers)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withRemoveMemberFromProteusGroupFailed() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteMembers)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withRemoveMemberFromMLSGroupSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::removeMembersFromMLSGroup)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withConversationProtocolIs(protocolInfo: ProtocolInfo) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::detailsById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(TestConversation.GROUP(protocolInfo)))
        }

        fun arrange() = this to removeMemberUseCase

        companion object {
            const val mlsGroupId = "mlsGroupId"
            val proteusProtocolInfo = ProtocolInfo.Proteus
            val mlsProtocolInfo = ProtocolInfo.MLS(mlsGroupId, groupState = ProtocolInfo.MLS.GroupState.ESTABLISHED)

        }
    }

}
