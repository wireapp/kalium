package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.MetadataDAO
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
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

        verify(arrangement.metadataDAO)
            .suspendFunction(arrangement.metadataDAO::insertValue)
            .with(eq("2"), eq(ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY))
            .wasInvoked(once)
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

        verify(arrangement.metadataDAO)
            .suspendFunction(arrangement.metadataDAO::insertValue)
            .with(eq("1"), eq(ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY))
            .wasInvoked(once)
    }

    class Arrangement {

        var apiVersion: Int = 0
        @Mock
        val metadataDAO = mock(MetadataDAO::class)
        var migrations: MutableList<Pair<Int, ApiMigration>> = mutableListOf()

        fun withCurrentApiVersion(apiVersion: Int) = apply {
            this.apiVersion = apiVersion
        }

        fun withPreviousApiVersion(apiVersion: Int) = apply {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKey)
                .whenInvokedWith(eq(ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY))
                .thenReturn(apiVersion.toString())
        }

        fun withMigration(api: Int, migration: ApiMigration) = apply {
            migrations.add(Pair(api, migration))
        }

        fun arrange() = this to ApiMigrationManager(apiVersion, metadataDAO, migrations)
    }

}
