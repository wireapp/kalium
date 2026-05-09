/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep
import dev.mokkery.matcher.any
import dev.mokkery.every
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface SyncMigrationStepsProviderArrangement {

    val syncMigrationStepsProvider: SyncMigrationStepsProvider

    fun withSyncMigrationSteps(
        steps: List<SyncMigrationStep>,
        fromVersion: (Int) -> Boolean = { true },
        toVersion: (Int) -> Boolean = { true }
    )
}

internal class SyncMigrationStepsProviderArrangementImpl : SyncMigrationStepsProviderArrangement {

        override val syncMigrationStepsProvider: SyncMigrationStepsProvider = mock<SyncMigrationStepsProvider>(mode = MockMode.autoUnit)

    override fun withSyncMigrationSteps(
        steps: List<SyncMigrationStep>,
        fromVersion: (Int) -> Boolean,
        toVersion: (Int) -> Boolean
    ) {
        every {
            syncMigrationStepsProvider.getMigrationSteps(
                matches { fromVersion(it) },
                matches { toVersion(it) }
            )
        }.returns(steps)
    }
}
