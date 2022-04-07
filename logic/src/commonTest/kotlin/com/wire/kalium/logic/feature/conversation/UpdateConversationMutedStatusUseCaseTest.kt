package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateConversationMutedStatusUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    private lateinit var updateConversationMutedStatus: UpdateConversationMutedStatusUseCase

    @BeforeTest
    fun setup() {
        updateConversationMutedStatus = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenAConversationId_whenChangingInvokingAChange_thenShouldDelegateTheCallAnReturnASuccessResult() = runTest {
        val conversationId = TestConversation.ID
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateMutedStatus)
            .whenInvokedWith(any(), eq(MutedConversationStatus.ALL_MUTED), any())
            .thenReturn(Either.Right(Unit))

        val result = updateConversationMutedStatus(conversationId, MutedConversationStatus.ALL_MUTED)
        assertEquals(ConversationUpdateStatusResult.Success::class, result::class)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateMutedStatus)
            .with(any(), eq(MutedConversationStatus.ALL_MUTED), any())
            .wasInvoked(exactly = once)

    }

}
