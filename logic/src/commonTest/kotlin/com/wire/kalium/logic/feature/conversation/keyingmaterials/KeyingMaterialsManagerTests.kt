package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyingMaterialsManagerTests {

    @Test
    fun givenKeyingMaterialManager_whenObservingSyncFinishes_updateKeyingMaterialsUseCasePerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withUpdateKeyingMaterialsSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verify(arrangement.updateKeyingMaterialsUseCase)
                .suspendFunction(arrangement.updateKeyingMaterialsUseCase::invoke)
                .wasInvoked(once)
        }

    // update keying materials failed for one
    // update keying materials succeed
    // time passed
    // time not passed

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val updateKeyingMaterialsUseCase = mock(classOf<UpdateKeyingMaterialsUseCase>())

        @Mock
        val timestampKeyRepository = mock(classOf<TimestampKeyRepository>())

        fun withUpdateKeyingMaterialsSuccessful() = apply {
            given(updateKeyingMaterialsUseCase)
                .suspendFunction(updateKeyingMaterialsUseCase::invoke)
                .whenInvoked()
                .thenReturn(UpdateKeyingMaterialsResult.Success)
        }

        fun arrange() = this to KeyingMaterialsManagerImpl(
            incrementalSyncRepository,
            lazy { updateKeyingMaterialsUseCase },
            lazy { timestampKeyRepository },
            TestKaliumDispatcher
        )
    }
}
