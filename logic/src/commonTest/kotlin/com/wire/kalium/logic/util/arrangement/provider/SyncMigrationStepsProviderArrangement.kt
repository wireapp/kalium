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
package com.wire.kalium.logic.util.arrangement.provider

import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProviderTest
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

internal interface SyncMigrationStepsProviderArrangement {

    val syncMigrationStepsProvider: SyncMigrationStepsProvider

    fun withSyncMigrationSteps(
        steps: List<SyncMigrationStep>,
        fromVersion: Matcher<Int> = any(),
        toVersion: Matcher<Int> = any()
    )
}

internal class SyncMigrationStepsProviderArrangementImpl : SyncMigrationStepsProviderArrangement {

    @Mock
    override val syncMigrationStepsProvider: SyncMigrationStepsProvider = mock(SyncMigrationStepsProvider::class)

    override fun withSyncMigrationSteps(
        steps: List<SyncMigrationStep>,
        fromVersion: Matcher<Int>,
        toVersion: Matcher<Int>
    ) {
        given(syncMigrationStepsProvider)
            .function(syncMigrationStepsProvider::getMigrationSteps)
            .whenInvokedWith(fromVersion, toVersion)
            .thenReturn(steps)
    }
}
