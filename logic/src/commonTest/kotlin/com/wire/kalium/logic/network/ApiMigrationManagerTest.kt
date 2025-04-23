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

package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.MetadataDAO
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiMigrationMock : ApiMigration {

    var hasBeenPerformed = false

    override suspend operator fun invoke(): Either<CoreFailure, Unit> {
        hasBeenPerformed = true
        return Either.Right(Unit)
    }

}

class ApiMigrationManagerTest {

    @Test
    fun givenUpgradeFromApi0ToApi1_whenCallingApplyUpgradesForApiVersion_thenMigrationIsPerformed() = runTest {
        val migration = ApiMigrationMock()

        val (_, apiUpgradeManager) = Arrangement()
            .withMigration(1, migration)
            .withPreviousApiVersion(0)
            .withCurrentApiVersion(1)
            .arrange()

        apiUpgradeManager.performMigrations()
        assertTrue(migration.hasBeenPerformed)
    }

    @Test
    fun givenUpgradeFromApi0ToApi2_whenCallingApplyUpgradesForApiVersion_thenAllMigrationsArePerformed() = runTest {
        val migration1 = ApiMigrationMock()
        val migration2 = ApiMigrationMock()

        val (_, apiUpgradeManager) = Arrangement()
            .withMigration(1, migration1)
            .withMigration(2, migration2)
            .withPreviousApiVersion(0)
            .withCurrentApiVersion(2)
            .arrange()

        apiUpgradeManager.performMigrations()

        assertTrue(migration1.hasBeenPerformed)
        assertTrue(migration2.hasBeenPerformed)
    }

    @Test
    fun givenUpgradeFromApi1ToApi2_whenCallingApplyUpgradesForApiVersion_thenPreviousMigrationsAreNotPerformed() = runTest {
        val migration1 = ApiMigrationMock()
        val migration2 = ApiMigrationMock()

        val (_, apiUpgradeManager) = Arrangement()
            .withMigration(1, migration1)
            .withMigration(2, migration2)
            .withPreviousApiVersion(1)
            .withCurrentApiVersion(2)
            .arrange()

        apiUpgradeManager.performMigrations()

        assertFalse(migration1.hasBeenPerformed)
    }

    @Test
    fun givenUpgradeFromApi1ToApi2_whenCallingApplyUpgradesForApiVersion_thenFutureMigrationsAreNotPerformed() = runTest {
        val migration3 = ApiMigrationMock()

        val (_, apiUpgradeManager) = Arrangement()
            .withMigration(3, migration3)
            .withPreviousApiVersion(1)
            .withCurrentApiVersion(2)
            .arrange()

        apiUpgradeManager.performMigrations()

        assertFalse(migration3.hasBeenPerformed)
    }

    @Test
    fun givenUpgradeFromApi1ToApi2_whenCallingApplyUpgradesForApiVersion_thenPersistedLastApiVersionIsUpdated() = runTest {
        val migration = ApiMigrationMock()

        val (arrangement, apiUpgradeManager) = Arrangement()
            .withMigration(1, migration)
            .withPreviousApiVersion(1)
            .withCurrentApiVersion(2)
            .arrange()

        apiUpgradeManager.performMigrations()

        coVerify {
            arrangement.metadataDAO.insertValue(eq("2"), eq(ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY))
        }.wasInvoked(once)
    }

    @Test
    fun givenDowngradeFromApi2ToApi1_whenCallingApplyUpgradesForApiVersion_thenPersistedLastApiVersionIsUpdated() = runTest {
        val migration1 = ApiMigrationMock()
        val migration2 = ApiMigrationMock()

        val (arrangement, apiUpgradeManager) = Arrangement()
            .withMigration(1, migration1)
            .withMigration(2, migration2)
            .withPreviousApiVersion(2)
            .withCurrentApiVersion(1)
            .arrange()

        apiUpgradeManager.performMigrations()

        coVerify {
            arrangement.metadataDAO.insertValue(eq("1"), eq(ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY))
        }.wasInvoked(once)
    }

    class Arrangement {

        var apiVersion: Int = 0
        val metadataDAO = mock(MetadataDAO::class)
        var migrations: MutableList<Pair<Int, ApiMigration>> = mutableListOf()

        fun withCurrentApiVersion(apiVersion: Int) = apply {
            this.apiVersion = apiVersion
        }

        suspend fun withPreviousApiVersion(apiVersion: Int) = apply {
            coEvery {
                metadataDAO.valueByKey(eq(ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY))
            }.returns(apiVersion.toString())
        }

        fun withMigration(api: Int, migration: ApiMigration) = apply {
            migrations.add(Pair(api, migration))
        }

        fun arrange() = this to ApiMigrationManager(apiVersion, metadataDAO, migrations)
    }

}
