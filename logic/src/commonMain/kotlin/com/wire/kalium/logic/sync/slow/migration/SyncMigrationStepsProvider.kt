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
package com.wire.kalium.logic.sync.slow.migration

import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep_6_7

internal interface SyncMigrationStepsProvider {
    fun getMigrationSteps(fromVersion: Int, toVersion: Int): List<SyncMigrationStep>
}

@Suppress("MagicNumber")
internal class SyncMigrationStepsProviderImpl(
    accountRepository: Lazy<AccountRepository>,
    selfTeamIdProvider: SelfTeamIdProvider
) : SyncMigrationStepsProvider {

    private val steps = mapOf(
        7 to lazy { SyncMigrationStep_6_7(accountRepository, selfTeamIdProvider) }
    )

    override fun getMigrationSteps(fromVersion: Int, toVersion: Int): List<SyncMigrationStep> {
        return steps
            .filter { it.key in (fromVersion + 1)..toVersion }.values
            .sortedBy { it.value.version }
            .map { it.value }
    }
}
