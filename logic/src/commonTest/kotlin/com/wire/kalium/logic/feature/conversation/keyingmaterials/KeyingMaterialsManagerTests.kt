package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
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

            arrangement.syncRepository.updateSyncState { SyncState.Live }
            yield()

            verify(arrangement.updateKeyingMaterialsUseCase)
                .suspendFunction(arrangement.updateKeyingMaterialsUseCase::invoke)
                .wasInvoked(once)
        }

    private class Arrangement {

        val syncRepository: SyncRepository = InMemorySyncRepository()

        @Mock
        val updateKeyingMaterialsUseCase = mock(classOf<UpdateKeyingMaterialsUseCase>())

        fun withUpdateKeyingMaterialsSuccessful() = apply {
            given(updateKeyingMaterialsUseCase)
                .suspendFunction(updateKeyingMaterialsUseCase::invoke)
                .whenInvoked()
                .thenReturn(UpdateKeyingMaterialsResult.Success)
        }

        fun arrange() = this to KeyingMaterialsManagerImpl(
            syncRepository,
            lazy { updateKeyingMaterialsUseCase },
            TestKaliumDispatcher
        )
    }
}
