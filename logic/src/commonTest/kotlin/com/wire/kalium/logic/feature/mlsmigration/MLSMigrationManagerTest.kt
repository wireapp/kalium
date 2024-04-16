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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

class MLSMigrationManagerTest {

    @Test
    fun givenMigrationUpdateTimerHasElapsed_whenObservingAndSyncFinishes_migrationIsUpdated() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withLastMLSMigrationCheck(true)
                .withRunMigrationSucceeds()
                .withLastMLSMigrationCheckResetSucceeds()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasInvoked(once)
        }

    @Test
    fun givenMigrationUpdateTimerHasNotElapsed_whenObservingSyncFinishes_migrationIsNotUpdated() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withLastMLSMigrationCheck(false)
                .withRunMigrationSucceeds()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingSyncFinishes_migrationIsNotUpdated() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .withRunMigrationSucceeds()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasNotInvoked()
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenObservingSyncFinishes_migrationIsNotUpdated() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withRunMigrationSucceeds()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasNotInvoked()
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        val kaliumConfigs = KaliumConfigs()

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val featureSupport = mock(FeatureSupport::class)

        @Mock
        val timestampKeyRepository = mock(TimestampKeyRepository::class)

        @Mock
        val mlsMigrationWorker = mock(MLSMigrationWorker::class)

        suspend fun withRunMigrationSucceeds() = apply {
            coEvery {
                mlsMigrationWorker.runMigration()
            }.returns(Either.Right(Unit))
        }

        suspend fun withLastMLSMigrationCheck(hasPassed: Boolean) = apply {
            coEvery {
                timestampKeyRepository.hasPassed(eq(TimestampKeys.LAST_MLS_MIGRATION_CHECK), any())
            }.returns(Either.Right(hasPassed))
        }

        suspend fun withLastMLSMigrationCheckResetSucceeds() = apply {
            coEvery {
                timestampKeyRepository.reset(eq(TimestampKeys.LAST_MLS_MIGRATION_CHECK))
            }.returns(Either.Right(Unit))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(supported)
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            coEvery {
                clientRepository.hasRegisteredMLSClient()
            }.returns(Either.Right(result))
        }

        fun arrange() = this to MLSMigrationManagerImpl(
            kaliumConfigs,
            featureSupport,
            incrementalSyncRepository,
            lazy { clientRepository },
            lazy { timestampKeyRepository },
            lazy { mlsMigrationWorker },
            TestKaliumDispatcher
        )
    }
}
