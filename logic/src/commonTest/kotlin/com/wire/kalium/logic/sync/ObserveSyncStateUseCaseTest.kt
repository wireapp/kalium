package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.InMemorySlowSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import kotlin.test.BeforeTest

class ObserveSyncStateUseCaseTest {

    private lateinit var slowSyncRepository: SlowSyncRepository
    private lateinit var incrementalSyncRepository: IncrementalSyncRepository
    private lateinit var observeSyncState: ObserveSyncStateUseCase

    @BeforeTest
    fun setup() {
        slowSyncRepository = InMemorySlowSyncRepository()
        incrementalSyncRepository = InMemoryIncrementalSyncRepository()
        observeSyncState = ObserveSyncStateUseCase(slowSyncRepository, incrementalSyncRepository)
    }

    // TODO(test): Add tests
}
