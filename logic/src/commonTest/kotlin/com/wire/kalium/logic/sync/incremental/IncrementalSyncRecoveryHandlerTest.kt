package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.SlowSyncRepository
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
        arrangement.recoverWithFailure(CoreFailure.Unknown(IllegalStateException()))

        with(arrangement) {
            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::clearLastSlowSyncCompletionInstant)
                .wasNotInvoked()

            verify(onRetryCallback)
                .function(onRetryCallback::retry)
                .with()
                .wasInvoked()
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

        with(arrangement) {
            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::clearLastSlowSyncCompletionInstant)
                .wasNotInvoked()

            verify(onRetryCallback)
                .function(onRetryCallback::retry)
                .with()
                .wasInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val onRetryCallback: OnRetryCallback = mock(classOf<OnRetryCallback>())

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(classOf<SlowSyncRepository>())

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(slowSyncRepository)
        }

        suspend fun recoverWithFailure(failure: CoreFailure) {
            incrementalSyncRecoveryHandler.recover(failure, onRetryCallback)
        }

        fun arrange() = this
    }

}
