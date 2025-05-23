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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MLSMigrationManagerTest {

    private val testScope: TestScope = TestScope()

    @Test
    fun givenMigrationUpdateTimerHasElapsed_whenObservingAndSyncFinishes_migrationIsUpdated() =
        testScope.runTest {
            val (arrangement, mLSMigrationManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withLastMLSMigrationCheck(true)
                .withRunMigrationSucceeds()
                .withLastMLSMigrationCheckResetSucceeds()
                .withWaitUntilLiveOrFailure(Unit.right())
                .arrange(testScope)

            mLSMigrationManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasInvoked(once)
        }

    @Test
    fun givenMigrationUpdateTimerHasNotElapsed_whenObservingSyncFinishes_migrationIsNotUpdated() =
        testScope.runTest {
            val (arrangement, mLSMigrationManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withLastMLSMigrationCheck(false)
                .withRunMigrationSucceeds()
                .withWaitUntilLiveOrFailure(Unit.right())
                .arrange(testScope)

            mLSMigrationManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingSyncFinishes_migrationIsNotUpdated() =
        testScope.runTest {
            val (arrangement, mLSMigrationManager) = Arrangement()
                .withIsMLSSupported(false)
                .withRunMigrationSucceeds()
                .withWaitUntilLiveOrFailure(Unit.right())
                .arrange(testScope)

            mLSMigrationManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasNotInvoked()
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenObservingSyncFinishes_migrationIsNotUpdated() =
        testScope.runTest {
            val (arrangement, mLSMigrationManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withRunMigrationSucceeds()
                .withWaitUntilLiveOrFailure(Unit.right())
                .arrange(testScope)

            mLSMigrationManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.mlsMigrationWorker.runMigration()
            }.wasNotInvoked()
        }

    private class Arrangement {
        @Mock
        val syncStateObserver: SyncStateObserver = mock(SyncStateObserver::class)

        val kaliumConfigs = KaliumConfigs()

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val isMLSEnabledUseCase = mock(IsMLSEnabledUseCase::class)

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

        suspend fun withIsMLSSupported(supported: Boolean) = apply {
            coEvery {
                isMLSEnabledUseCase()
            }.returns(supported)

        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            coEvery {
                clientRepository.hasRegisteredMLSClient()
            }.returns(Either.Right(result))
        }

        fun withSyncStates(flow: StateFlow<SyncState>) = apply {
            every {
                syncStateObserver.syncState
            }.returns(flow)
        }

        suspend fun withWaitUntilLiveOrFailure(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                syncStateObserver.waitUntilLiveOrFailure()
            }.returns(result)
        }

        fun arrange(coroutineScope: CoroutineScope) = this to MLSMigrationManager(
            kaliumConfigs,
            isMLSEnabledUseCase,
            syncStateObserver,
            lazy { clientRepository },
            lazy { timestampKeyRepository },
            lazy { mlsMigrationWorker },
            coroutineScope,
        )
    }
}
