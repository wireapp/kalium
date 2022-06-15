package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
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

class AcceptConnectionRequestUseCaseTest {

    @Mock
    private val connectionRepository: ConnectionRepository = mock(ConnectionRepository::class)

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    lateinit var acceptConnectionRequestUseCase: AcceptConnectionRequestUseCase

    @BeforeTest
    fun setUp() {
        acceptConnectionRequestUseCase = AcceptConnectionRequestUseCaseImpl(connectionRepository, conversationRepository)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingAcceptConnectionRequestAndOk_thenShouldReturnsASuccessResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.ACCEPTED))
            .thenReturn(Either.Right(connection))

        given(conversationRepository)
            .suspendFunction(conversationRepository::fetchConversation)
            .whenInvokedWith(eq(conversationId))
            .thenReturn(Either.Right(Unit))

        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationModifiedDate)
            .whenInvokedWith(eq(conversationId), any())
            .thenReturn(Either.Right(Unit))

        // when
        val resultOk = acceptConnectionRequestUseCase(userId)

        // then
        assertEquals(AcceptConnectionRequestUseCaseResult.Success, resultOk)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.ACCEPTED))
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingAcceptConnectionRequestAndFails_thenShouldReturnsAFailureResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.ACCEPTED))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))

        // when
        val resultFailure = acceptConnectionRequestUseCase(userId)

        // then
        assertEquals(AcceptConnectionRequestUseCaseResult.Failure::class, resultFailure::class)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.ACCEPTED))
            .wasInvoked(once)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
        val conversationId = ConversationId("someId", "someDomain")
        val connection = Connection(
            "someId",
            "from",
            "lastUpdate",
            conversationId,
            conversationId,
            ConnectionState.ACCEPTED,
            "toId",
            null
        )
    }
}
