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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.sync.SyncStateObserver
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyingMaterialsManagerTests {
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope()
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun givenLastCheckTimestampKeyHasPassedAndUpdateKeyingMaterialsSucceeded_whenObservingAndSyncFinishes_TimestampKeyResetCalled() =
        testScope.runTest {

            val (arrangement, keyingMaterialManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withUpdateKeyingMaterialIs(UpdateKeyingMaterialsResult.Success)
                .withTimestampKeyCheck(true)
                .withTimestampKeyResetSuccessful()
                .withSyncStates(Unit.right())
                .arrange(testScope)

            keyingMaterialManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasInvoked(once)
            coVerify {
                arrangement.timestampKeyRepository.reset(eq(TimestampKeys.LAST_KEYING_MATERIAL_UPDATE_CHECK))
            }.wasInvoked(once)
        }

    @Test
    fun givenLastCheckTimestampKeyHasNotPassed_whenObservingAndSyncFinishes_updateKeyingMaterialsUseCaseNotPerformed() =
        testScope.runTest {

            val (arrangement, keyingMaterialManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withUpdateKeyingMaterialIs(UpdateKeyingMaterialsResult.Success)
                .withTimestampKeyCheck(false)
                .withSyncStates(Unit.right())
                .arrange(testScope)

            keyingMaterialManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasNotInvoked()
            coVerify {
                arrangement.timestampKeyRepository.reset(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingAndSyncFinishes_updateKeyingMaterialsUseCaseNotPerformed() =
        testScope.runTest {

            val (arrangement, keyingMaterialManager) = Arrangement()
                .withIsMLSSupported(false)
                .withSyncStates(Unit.right())
                .arrange(testScope)

            keyingMaterialManager.invoke()
            advanceUntilIdle()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSClientHasNotBeenRegistered_whenObservingAndSyncFinishes_updateKeyingMaterialsUseCaseNotPerformed() =
        testScope.runTest {

            val (arrangement, keyingMaterialManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withSyncStates(Unit.right())
                .arrange(testScope)

            keyingMaterialManager.invoke()
            advanceUntilIdle()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasNotInvoked()
        }

    @Test
    fun givenLastCheckTimestampKeyHasPassedAndUpdateKeyingMaterialsFailed_whenObservingAndSyncFinishes_TimestampKeyResetNotCalled() =
        testScope.runTest {

            val (arrangement, keyingMaterialManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withUpdateKeyingMaterialIs(UpdateKeyingMaterialsResult.Failure(StorageFailure.DataNotFound))
                .withTimestampKeyCheck(true)
                .withTimestampKeyResetSuccessful()
                .withSyncStates(Unit.right())
                .arrange(testScope)

            keyingMaterialManager.invoke()
            advanceUntilIdle()
            coVerify {
                arrangement.updateKeyingMaterialsUseCase.invoke()
            }.wasInvoked(once)
            coVerify {
                arrangement.timestampKeyRepository.reset(any())
            }.wasNotInvoked()
        }

    private class Arrangement {

        val syncStateObserver: SyncStateObserver = mock(SyncStateObserver::class)
        val clientRepository = mock(ClientRepository::class)
        val featureSupport = mock(FeatureSupport::class)
        val updateKeyingMaterialsUseCase = mock(UpdateKeyingMaterialsUseCase::class)
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

        suspend fun withSyncStates(result : Either<CoreFailure, Unit>) = apply {
            coEvery {
                syncStateObserver.waitUntilLiveOrFailure()
            }.returns(result)
        }

        fun arrange(testScope: TestScope) = this to KeyingMaterialsManager(
            featureSupport,
            syncStateObserver,
            lazy { clientRepository },
            lazy { updateKeyingMaterialsUseCase },
            lazy { timestampKeyRepository },
            testScope
        )
    }
}
