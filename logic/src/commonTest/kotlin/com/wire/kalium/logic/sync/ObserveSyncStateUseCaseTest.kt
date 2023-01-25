/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlin.test.BeforeTest

class ObserveSyncStateUseCaseTest {

    private lateinit var slowSyncRepository: SlowSyncRepository
    private lateinit var incrementalSyncRepository: IncrementalSyncRepository
    private lateinit var observeSyncState: ObserveSyncStateUseCase

    @Mock
    val sessionRepository = mock(classOf<SessionRepository>())

    @BeforeTest
    fun setup() {
        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"))
        slowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)
        incrementalSyncRepository = InMemoryIncrementalSyncRepository()
        observeSyncState = ObserveSyncStateUseCase(slowSyncRepository, incrementalSyncRepository)
    }

    // TODO(test): Add tests
}
