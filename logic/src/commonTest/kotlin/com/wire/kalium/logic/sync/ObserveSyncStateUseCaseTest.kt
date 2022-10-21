package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlin.test.BeforeTest

class ObserveSyncStateUseCaseTest {

    private lateinit var slowSyncRepository: SlowSyncRepository
    private lateinit var incrementalSyncRepository: IncrementalSyncRepository
    private lateinit var observeSyncState: ObserveSyncStateUseCase

    @BeforeTest
    fun setup() {
        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"))
        slowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)
        incrementalSyncRepository = InMemoryIncrementalSyncRepository()
        observeSyncState = ObserveSyncStateUseCase(slowSyncRepository, incrementalSyncRepository)
    }

    // TODO(test): Add tests
}
