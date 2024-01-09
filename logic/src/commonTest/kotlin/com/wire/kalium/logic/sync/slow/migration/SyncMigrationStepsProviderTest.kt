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

import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep_6_7
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.AccountRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.AccountRepositoryArrangementImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncMigrationStepsProviderTest {

    @Test
    fun givenAllMigrationsAreRequested_thenAllCorrectStepsAreReturnedInOrder() {

        val (_, provider) = Arrangement().arrange()

        provider.getMigrationSteps(Int.MIN_VALUE, Int.MAX_VALUE).also {
            assertIs<SyncMigrationStep_6_7>(it.first())
            assertEquals(7, it.first().version)
        }
    }

    @Test
    fun givenTheRequestedVersionHaveNoSteps_thenReturnEmptyList() {

        val (_, provider) = Arrangement().arrange()

        provider.getMigrationSteps(7, 10).also {
            assertEquals(0, it.size)
        }
    }

    private class Arrangement : AccountRepositoryArrangement by AccountRepositoryArrangementImpl(),
        SelfTeamIdProviderArrangement by SelfTeamIdProviderArrangementImpl() {

        private val provider: SyncMigrationStepsProvider = SyncMigrationStepsProviderImpl(
            lazy { accountRepository },
            selfTeamIdProvider
        )

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to provider }
    }
}
