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

package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ApiMigrationV3Test {

    @Test
    fun givenNoClientIsRegistered_whenInvokingMigration_thenMigrationIsSuccessful() = runTest {
        val (_, migration) = Arrangement()
            .withClientId(null)
            .arrange()

        migration.invoke().shouldSucceed()
    }

    @Test
    fun givenSessionUpgradeIsSuccessful_whenInvokingMigration_thenMigrationIsSuccessful() = runTest {
        val (arrangement, migration) = Arrangement()
            .withClientId(TestClient.CLIENT_ID)
            .withUpgradeCurrentSessionReturning(Either.Right(Unit))
            .arrange()

        migration.invoke().shouldSucceed()

        verify(arrangement.upgradeCurrentSessionUseCase)
            .suspendFunction(arrangement.upgradeCurrentSessionUseCase::invoke)
            .with(eq(TestClient.CLIENT_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSessionUpgradeIsFailing_whenInvokingMigration_thenMigrationIsFailing() = runTest {
        val (_, migration) = Arrangement()
            .withClientId(TestClient.CLIENT_ID)
            .withUpgradeCurrentSessionReturning(Either.Left(NetworkFailure.NoNetworkConnection(cause = null)))
            .arrange()

        migration.invoke().shouldFail()
    }

    class Arrangement {

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val upgradeCurrentSessionUseCase = mock(classOf<UpgradeCurrentSessionUseCase>())

        fun withClientId(clientId: ClientId?) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(clientId?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound))
        }

        fun withUpgradeCurrentSessionReturning(result: Either<CoreFailure, Unit>) = apply {
            given(upgradeCurrentSessionUseCase)
                .suspendFunction(upgradeCurrentSessionUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to ApiMigrationV3(currentClientIdProvider, upgradeCurrentSessionUseCase)
    }

}
