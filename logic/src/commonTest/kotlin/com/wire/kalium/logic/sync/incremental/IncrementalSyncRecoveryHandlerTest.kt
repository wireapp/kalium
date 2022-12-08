package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import io.mockative.Mock
import io.mockative.any
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

        // then
        with(arrangement) {
            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::clearLastSlowSyncCompletionInstant)
                .wasNotInvoked()

            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::updateMLSNeedsRecovery)
                .with(any())
                .wasNotInvoked()

            verify(onIncrementalSyncRetryCallback)
                .function(onIncrementalSyncRetryCallback::retry)
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

        // then
        with(arrangement) {
            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::clearLastSlowSyncCompletionInstant)
                .wasNotInvoked()

            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::updateMLSNeedsRecovery)
                .with(any())
                .wasNotInvoked()

            verify(onIncrementalSyncRetryCallback)
                .function(onIncrementalSyncRetryCallback::retry)
                .with()
                .wasInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val onIncrementalSyncRetryCallback: OnIncrementalSyncRetryCallback = mock(classOf<OnIncrementalSyncRetryCallback>())

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(classOf<SlowSyncRepository>())

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(slowSyncRepository)
        }

        suspend fun recoverWithFailure(failure: CoreFailure) {
            incrementalSyncRecoveryHandler.recover(failure, onIncrementalSyncRetryCallback)
        }

        fun arrange() = this
    }

}
