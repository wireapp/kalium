/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.featureFlags.FeatureSupport
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
            verify(arrangement.updateKeyingMaterialsUseCase)
                .suspendFunction(arrangement.updateKeyingMaterialsUseCase::invoke)
                .wasInvoked(once)
            verify(arrangement.timestampKeyRepository)
                .suspendFunction(arrangement.timestampKeyRepository::reset)
                .with(eq(TimestampKeys.LAST_KEYING_MATERIAL_UPDATE_CHECK))
                .wasInvoked(once)
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
            verify(arrangement.updateKeyingMaterialsUseCase)
                .suspendFunction(arrangement.updateKeyingMaterialsUseCase::invoke)
                .wasNotInvoked()
            verify(arrangement.timestampKeyRepository)
                .suspendFunction(arrangement.timestampKeyRepository::reset)
                .with(anything())
                .wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingAndSyncFinishes_updateKeyingMaterialsUseCaseNotPerformed() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()
            verify(arrangement.updateKeyingMaterialsUseCase)
                .suspendFunction(arrangement.updateKeyingMaterialsUseCase::invoke)
                .wasNotInvoked()
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
            verify(arrangement.updateKeyingMaterialsUseCase)
                .suspendFunction(arrangement.updateKeyingMaterialsUseCase::invoke)
                .wasNotInvoked()
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
            verify(arrangement.updateKeyingMaterialsUseCase)
                .suspendFunction(arrangement.updateKeyingMaterialsUseCase::invoke)
                .wasInvoked(once)
            verify(arrangement.timestampKeyRepository)
                .suspendFunction(arrangement.timestampKeyRepository::reset)
                .with(anything())
                .wasNotInvoked()
        }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val updateKeyingMaterialsUseCase = mock(classOf<UpdateKeyingMaterialsUseCase>())

        @Mock
        val timestampKeyRepository = mock(classOf<TimestampKeyRepository>())

        fun withTimestampKeyCheck(hasPassed: Boolean) = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::hasPassed)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(hasPassed))
        }

        fun withTimestampKeyResetSuccessful() = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::reset)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateKeyingMaterialIs(result: UpdateKeyingMaterialsResult) = apply {
            given(updateKeyingMaterialsUseCase)
                .suspendFunction(updateKeyingMaterialsUseCase::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun withHasRegisteredMLSClient(result: Boolean) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(Either.Right(result))
        }

        fun arrange() = this to KeyingMaterialsManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            lazy { clientRepository },
            lazy { updateKeyingMaterialsUseCase },
            lazy { timestampKeyRepository },
            TestKaliumDispatcher
        )
    }
}
