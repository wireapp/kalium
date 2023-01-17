package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
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
            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::clearLastSlowSyncCompletionInstant)
                .wasInvoked(once)

            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::setNeedsToRecoverMLSGroups)
                .with(eq(true))
                .wasInvoked(once)

            verify(addSystemMessageToAllConversationsUseCase)
                .function(addSystemMessageToAllConversationsUseCase::invoke)
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
            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::clearLastSlowSyncCompletionInstant)
                .wasNotInvoked()

            verify(slowSyncRepository)
                .suspendFunction(slowSyncRepository::setNeedsToRecoverMLSGroups)
                .with(any())
                .wasNotInvoked()

            verify(onIncrementalSyncRetryCallback)
                .function(onIncrementalSyncRetryCallback::retry)
                .with()
                .wasInvoked()

            verify(addSystemMessageToAllConversationsUseCase)
                .function(addSystemMessageToAllConversationsUseCase::invoke)
                .with()
                .wasNotInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val onIncrementalSyncRetryCallback: OnIncrementalSyncRetryCallback = mock(classOf<OnIncrementalSyncRetryCallback>())

        @Mock
        val addSystemMessageToAllConversationsUseCase: AddSystemMessageToAllConversationsUseCase =
            mock(classOf<AddSystemMessageToAllConversationsUseCase>())

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(classOf<SlowSyncRepository>())

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(slowSyncRepository, addSystemMessageToAllConversationsUseCase)
        }

        suspend fun recoverWithFailure(failure: CoreFailure) {
            incrementalSyncRecoveryHandler.recover(failure, onIncrementalSyncRetryCallback)
        }

        fun arrange() = this
    }

}
