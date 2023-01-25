package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPropertiesEventReceiverTest {

    @Test
    fun givenReadReceiptsUpdateEvent_repositoryIsInvoked() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateReadReceiptsSuccess()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setReadReceiptsStatus)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val userPropertiesEventReceiver: UserPropertiesEventReceiver = UserPropertiesEventReceiverImpl(
            userConfigRepository = userConfigRepository
        )

        fun withUpdateReadReceiptsSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setReadReceiptsStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to userPropertiesEventReceiver
    }
}
