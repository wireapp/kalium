package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendTextMessageCaseTest {

    @Test
    fun givenAValidMessage_whenSendingSomeText_thenShouldReturnASuccessResult() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement()
            .withSuccessfulResponse()
            .arrange()

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text")

        // Then
        assertTrue(result is Either.Right)
        assertEquals(SlowSyncStatus.Complete, arrangement.completeStateFlow.value)

        verify(arrangement.userPropertyRepository)
            .suspendFunction(arrangement.userPropertyRepository::getReadReceiptsStatus)
            .wasInvoked(once)
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasInvoked(once)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendPendingMessage)
            .with(any(), any())
            .wasInvoked(once)

    }

    private class Arrangement {

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val slowSyncRepository = mock(classOf<SlowSyncRepository>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        val userPropertyRepository = mock(classOf<UserPropertyRepository>())

        val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

        fun withSuccessfulResponse(): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(flowOf())
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestClient.CLIENT_ID))
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(completeStateFlow)
            given(messageSender)
                .suspendFunction(messageSender::sendPendingMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))

            withToggleReadReceiptsStatus()
            return this
        }

        fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::getReadReceiptsStatus)
                .whenInvoked()
                .thenReturn(enabled)
        }

        fun arrange() = this to SendTextMessageUseCase(
            persistMessage,
            TestUser.SELF.id,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            userPropertyRepository
        )
    }

}
