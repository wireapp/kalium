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

package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyingMaterialsManagerTests {
    @Test
    fun givenLastCheckTimestampKeyHasPassedAndUpdateKeyingMaterialsSucceeded_whenObservingAndSyncFinishes_TimestampKeyResetCalled() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withUpdateKeyingMaterialIs(UpdateKeyingMaterialsResult.Success)
                .withTimestampKeyCheck(true)
                .withTimestampKeyResetSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasInvoked(once)
            coVerify {
                arrangement.timestampKeyRepository.reset(eq(TimestampKeys.LAST_KEYING_MATERIAL_UPDATE_CHECK))
            }.wasInvoked(once)
        }

    @Test
    fun givenLastCheckTimestampKeyHasNotPassed_whenObservingAndSyncFinishes_updateKeyingMaterialsUseCaseNotPerformed() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withUpdateKeyingMaterialIs(UpdateKeyingMaterialsResult.Success)
                .withTimestampKeyCheck(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasNotInvoked()
            coVerify {
                arrangement.timestampKeyRepository.reset(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingAndSyncFinishes_updateKeyingMaterialsUseCaseNotPerformed() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSClientHasNotBeenRegistered_whenObservingAndSyncFinishes_updateKeyingMaterialsUseCaseNotPerformed() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasNotInvoked()
        }

    @Test
    fun givenLastCheckTimestampKeyHasPassedAndUpdateKeyingMaterialsFailed_whenObservingAndSyncFinishes_TimestampKeyResetNotCalled() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withUpdateKeyingMaterialIs(UpdateKeyingMaterialsResult.Failure(StorageFailure.DataNotFound))
                .withTimestampKeyCheck(true)
                .withTimestampKeyResetSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasInvoked(once)
            coVerify {
                arrangement.timestampKeyRepository.reset(any())
            }.wasNotInvoked()
        }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(SessionRepository::class)

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val featureSupport = mock(FeatureSupport::class)

        @Mock
        val updateKeyingMaterialsUseCase = mock(UpdateKeyingMaterialsUseCase::class)

        @Mock
        val timestampKeyRepository = mock(TimestampKeyRepository::class)

        suspend fun withTimestampKeyCheck(hasPassed: Boolean) = apply {
            coEvery {
                timestampKeyRepository.hasPassed(any(), any())
            }.returns(Either.Right(hasPassed))
        }

        suspend fun withTimestampKeyResetSuccessful() = apply {
            coEvery {
                timestampKeyRepository.reset(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateKeyingMaterialIs(result: UpdateKeyingMaterialsResult) = apply {
            coEvery {
                updateKeyingMaterialsUseCase.invoke()
            }.returns(result)
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

        fun arrange() = this to KeyingMaterialsManager(
            featureSupport,
            TODO(),
            lazy { clientRepository },
            lazy { updateKeyingMaterialsUseCase },
            lazy { timestampKeyRepository },
            TODO()
        )
    }
}
