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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncStateObserver
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsMigrationWorker.runMigration()
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.mlsMigrationWorker.runMigration()
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.mlsMigrationWorker.runMigration()
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.mlsMigrationWorker.runMigration()
            }
        }

    private class Arrangement {

        val syncStateObserver: SyncStateObserver = mock<SyncStateObserver>(mode = MockMode.autoUnit)
        val kaliumConfigs = KaliumConfigs()
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val isMLSEnabledUseCase = mock<IsMLSEnabledUseCase>(mode = MockMode.autoUnit)
        val timestampKeyRepository = mock<TimestampKeyRepository>(mode = MockMode.autoUnit)
        val mlsMigrationWorker = mock<MLSMigrationWorker>(mode = MockMode.autoUnit)

        suspend fun withRunMigrationSucceeds() = apply {
            everySuspend {
                mlsMigrationWorker.runMigration()
            } returns Either.Right(Unit)
        }

        suspend fun withLastMLSMigrationCheck(hasPassed: Boolean) = apply {
            everySuspend {
                timestampKeyRepository.hasPassed(eq(TimestampKeys.LAST_MLS_MIGRATION_CHECK), any())
            } returns Either.Right(hasPassed)
        }

        suspend fun withLastMLSMigrationCheckResetSucceeds() = apply {
            everySuspend {
                timestampKeyRepository.reset(eq(TimestampKeys.LAST_MLS_MIGRATION_CHECK))
            } returns Either.Right(Unit)
        }

        suspend fun withIsMLSSupported(supported: Boolean) = apply {
            everySuspend {
                isMLSEnabledUseCase()
            } returns supported

        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            everySuspend {
                clientRepository.hasRegisteredMLSClient()
            } returns Either.Right(result)
        }

        fun withSyncStates(flow: StateFlow<SyncState>) = apply {
            every {
                syncStateObserver.syncState
            } returns flow
        }

        suspend fun withWaitUntilLiveOrFailure(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                syncStateObserver.waitUntilLiveOrFailure()
            } returns result
        }

        fun arrange(coroutineScope: CoroutineScope) = this to MLSMigrationManagerImpl(
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
