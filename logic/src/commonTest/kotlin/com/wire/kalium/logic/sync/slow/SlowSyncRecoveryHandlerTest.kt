package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SlowSyncRecoveryHandlerTest {

    @Test
    fun givenSelfUserDeletedFailure_whenRecovering_thenLogoutTheUser() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(SelfUserDeleted)

        with(arrangement) {
            verify(logoutUseCase)
                .suspendFunction(logoutUseCase::invoke)
                .with(matching { LogoutReason.DELETED_ACCOUNT == it })
                .wasInvoked(once)

            verify(onSlowSyncRetryCallback)
                .function(onSlowSyncRetryCallback::retry)
                .with()
                .wasInvoked()
        }
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenRetrySlowSync() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(
            CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))
        )

        with(arrangement) {
            verify(logoutUseCase)
                .suspendFunction(logoutUseCase::invoke)
                .with(any())
                .wasNotInvoked()

            verify(onSlowSyncRetryCallback)
                .function(onSlowSyncRetryCallback::retry)
                .with()
                .wasInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val onSlowSyncRetryCallback: OnSlowSyncRetryCallback = mock(classOf<OnSlowSyncRetryCallback>())

        @Mock
        val logoutUseCase = configure(mock(classOf<LogoutUseCase>())) { stubsUnitByDefault = true }

        private val slowSyncRecoveryHandler by lazy {
            SlowSyncRecoveryHandlerImpl(logoutUseCase)
        }

        suspend fun recoverWithFailure(failure: CoreFailure) {
            slowSyncRecoveryHandler.recover(failure, onSlowSyncRetryCallback)
        }

        fun arrange() = this
    }

}
