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
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

            verify(arrangement.mlsMigrationWorker)
                .suspendFunction(arrangement.mlsMigrationWorker::runMigration)
                .wasInvoked(once)
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

            verify(arrangement.mlsMigrationWorker)
                .suspendFunction(arrangement.mlsMigrationWorker::runMigration)
                .wasNotInvoked()
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

            verify(arrangement.mlsMigrationWorker)
                .suspendFunction(arrangement.mlsMigrationWorker::runMigration)
                .wasNotInvoked()
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

            verify(arrangement.mlsMigrationWorker)
                .suspendFunction(arrangement.mlsMigrationWorker::runMigration)
                .wasNotInvoked()
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        val kaliumConfigs = KaliumConfigs()

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val isMLSEnabledUseCase = mock(classOf<IsMLSEnabledUseCase>())

        @Mock
        val timestampKeyRepository = mock(classOf<TimestampKeyRepository>())

        @Mock
        val mlsMigrationWorker = mock(classOf<MLSMigrationWorker>())

        fun withRunMigrationSucceeds() = apply {
            given(mlsMigrationWorker)
                .suspendFunction(mlsMigrationWorker::runMigration)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun withLastMLSMigrationCheck(hasPassed: Boolean) = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::hasPassed)
                .whenInvokedWith(eq(TimestampKeys.LAST_MLS_MIGRATION_CHECK), anything())
                .thenReturn(Either.Right(hasPassed))
        }

        fun withLastMLSMigrationCheckResetSucceeds() = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::reset)
                .whenInvokedWith(eq(TimestampKeys.LAST_MLS_MIGRATION_CHECK))
                .thenReturn(Either.Right(Unit))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(isMLSEnabledUseCase)
                .invocation { isMLSEnabledUseCase.invoke() }
                .thenReturn(supported)
        }

        fun withHasRegisteredMLSClient(result: Boolean) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(Either.Right(result))
        }

        fun arrange() = this to MLSMigrationManagerImpl(
            kaliumConfigs,
            isMLSEnabledUseCase,
            incrementalSyncRepository,
            lazy { clientRepository },
            lazy { timestampKeyRepository },
            lazy { mlsMigrationWorker },
            TestKaliumDispatcher
        )
    }
}
