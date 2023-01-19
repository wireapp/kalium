package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.test_util.TestNetworkException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IncrementalSyncRecoveryHandlerTest {

    @Test
    fun givenClientOrEventNotFoundFailure_whenRecovering_thenRestartSlowSyncProcess() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(NetworkFailure.ServerMiscommunication(TestNetworkException.notFound))

        // then
        with(arrangement) {
            verify(restartSlowSyncProcessForRecoveryUseCase)
                .function(restartSlowSyncProcessForRecoveryUseCase::invoke)
                .with()
                .wasInvoked()

            verify(onIncrementalSyncRetryCallback)
                .function(onIncrementalSyncRetryCallback::retry)
                .with()
                .wasNotInvoked()
        }
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenRetryIncrementalSync() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(
            CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))
        )

        // then
        with(arrangement) {
            verify(onIncrementalSyncRetryCallback)
                .function(onIncrementalSyncRetryCallback::retry)
                .with()
                .wasInvoked()

            verify(restartSlowSyncProcessForRecoveryUseCase)
                .function(restartSlowSyncProcessForRecoveryUseCase::invoke)
                .with()
                .wasNotInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val onIncrementalSyncRetryCallback = mock(classOf<OnIncrementalSyncRetryCallback>())

        @Mock
        val restartSlowSyncProcessForRecoveryUseCase = mock(classOf<RestartSlowSyncProcessForRecoveryUseCase>())

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(restartSlowSyncProcessForRecoveryUseCase)
        }

        suspend fun recoverWithFailure(failure: CoreFailure) {
            incrementalSyncRecoveryHandler.recover(failure, onIncrementalSyncRetryCallback)
        }

        fun arrange() = this
    }

}
