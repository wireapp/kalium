package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.InMemorySlowSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import io.mockative.given
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SyncManagerTest {

    @Suppress("unused")
    private class Arrangement {

        val slowSyncRepository: SlowSyncRepository = InMemorySlowSyncRepository()

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        fun withSlowSyncRepositoryReturning(slowSyncStatusFlow: StateFlow<SlowSyncStatus>) = apply {
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(slowSyncStatusFlow)
        }

        fun withSlowSyncComplete() = apply {
            withSlowSyncRepositoryReturning(MutableStateFlow(SlowSyncStatus.Complete))
        }

        private val syncManager = SyncManagerImpl(
            slowSyncRepository, incrementalSyncRepository
        )

        fun arrange() = this to syncManager
    }
}
