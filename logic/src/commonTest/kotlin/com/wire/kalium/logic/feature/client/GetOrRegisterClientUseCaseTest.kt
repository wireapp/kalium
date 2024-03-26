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


package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class GetOrRegisterClientUseCaseTest {

    //todo: fix later
    @Ignore
    @Test
    fun givenValidClientIsRetained_whenRegisteringAClient_thenDoNotRegisterNewAndReturnPersistedClient() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (arrangement, useCase) = Arrangement()
            .withRetainedClientIdResult(Either.Right(clientId))
            .withVerifyExistingClientResult(VerifyExistingClientResult.Success(client))
            .withUpgradeCurrentSessionResult(Either.Right(Unit))
            .withPersistClientIdResult(Either.Right(Unit))
            .arrange()

        val result = useCase.invoke(RegisterClientUseCase.RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verify(arrangement.registerClientUseCase)
            .suspendFunction(arrangement.registerClientUseCase::invoke)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.upgradeCurrentSessionUseCase)
            .suspendFunction(arrangement.upgradeCurrentSessionUseCase::invoke)
            .with(eq(clientId))
            .wasInvoked(exactly = once)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(eq(clientId))
            .wasInvoked(exactly = once)
    }

    //todo: fix later
    @Ignore
    @Test
    fun givenInvalidClientIsRetained_whenRegisteringAClient_thenClearDataAndRegisterNewClient() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (arrangement, useCase) = Arrangement()
            .withRetainedClientIdResult(Either.Right(clientId))
            .withVerifyExistingClientResult(VerifyExistingClientResult.Failure.ClientNotRegistered)
            .withRegisterClientResult(RegisterClientResult.Success(client))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUpgradeCurrentSessionResult(Either.Right(Unit))
            .withPersistClientIdResult(Either.Right(Unit))
            .withSetUpdateFirebaseTokenFlagResult(Either.Right(Unit))
            .arrange()

        val result = useCase.invoke(RegisterClientUseCase.RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verify(arrangement.cachedClientIdClearer)
            .function(arrangement.cachedClientIdClearer::invoke)
            .wasInvoked(exactly = once)
        verify(arrangement.clearClientDataUseCase)
            .suspendFunction(arrangement.clearClientDataUseCase::invoke)
            .wasInvoked(exactly = once)
        verify(arrangement.logoutRepository)
            .suspendFunction(arrangement.logoutRepository::clearClientRelatedLocalMetadata)
            .wasInvoked(exactly = once)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::clearRetainedClientId)
            .wasInvoked(exactly = once)
        verify(arrangement.pushTokenRepository)
            .suspendFunction(arrangement.pushTokenRepository::setUpdateFirebaseTokenFlag)
            .with(eq(true))
            .wasInvoked(exactly = once)
        verify(arrangement.registerClientUseCase)
            .suspendFunction(arrangement.registerClientUseCase::invoke)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.upgradeCurrentSessionUseCase)
            .suspendFunction(arrangement.upgradeCurrentSessionUseCase::invoke)
            .with(eq(clientId))
            .wasInvoked(exactly = once)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(eq(clientId))
            .wasInvoked(exactly = once)
    }

    //todo: fix later
    @Ignore
    @Test
    fun givenClientNotRetained_whenRegisteringAClient_thenRegisterNewClient() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (arrangement, useCase) = Arrangement()
            .withRetainedClientIdResult(Either.Left(CoreFailure.MissingClientRegistration))
            .withRegisterClientResult(RegisterClientResult.Success(client))
            .withUpgradeCurrentSessionResult(Either.Right(Unit))
            .withPersistClientIdResult(Either.Right(Unit))
            .arrange()

        val result = useCase.invoke(RegisterClientUseCase.RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verify(arrangement.registerClientUseCase)
            .suspendFunction(arrangement.registerClientUseCase::invoke)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.upgradeCurrentSessionUseCase)
            .suspendFunction(arrangement.upgradeCurrentSessionUseCase::invoke)
            .with(eq(clientId))
            .wasInvoked(exactly = once)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(eq(clientId))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val pushTokenRepository = mock(classOf<PushTokenRepository>())

        @Mock
        val logoutRepository = configure(mock(classOf<LogoutRepository>())) { stubsUnitByDefault = true }

        @Mock
        val registerClientUseCase = mock(classOf<RegisterClientUseCase>())

        @Mock
        val clearClientDataUseCase = configure(mock(classOf<ClearClientDataUseCase>())) { stubsUnitByDefault = true }

        @Mock
        val upgradeCurrentSessionUseCase = mock(classOf<UpgradeCurrentSessionUseCase>())

        @Mock
        val syncFeatureConfigsUseCase = mock(classOf<SyncFeatureConfigsUseCase>())

        @Mock
        val verifyExistingClientUseCase = mock(classOf<VerifyExistingClientUseCase>())

        @Mock
        val cachedClientIdClearer = configure(mock(classOf<CachedClientIdClearer>())) { stubsUnitByDefault = true }

        val getOrRegisterClientUseCase: GetOrRegisterClientUseCase = GetOrRegisterClientUseCaseImpl(
            clientRepository,
            pushTokenRepository,
            logoutRepository,
            registerClientUseCase,
            clearClientDataUseCase,
            verifyExistingClientUseCase,
            upgradeCurrentSessionUseCase,
            cachedClientIdClearer,
            syncFeatureConfigsUseCase
        )

        fun withRetainedClientIdResult(result: Either<CoreFailure, ClientId>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::retainedClientId)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withRegisterClientResult(result: RegisterClientResult) = apply {
            given(registerClientUseCase)
                .suspendFunction(registerClientUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withClearRetainedClientIdResult(result: Either<CoreFailure, Unit>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::clearRetainedClientId)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withPersistClientIdResult(result: Either<CoreFailure, Unit>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::persistClientId)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withVerifyExistingClientResult(result: VerifyExistingClientResult) = apply {
            given(verifyExistingClientUseCase)
                .suspendFunction(verifyExistingClientUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUpgradeCurrentSessionResult(result: Either<CoreFailure, Unit>) = apply {
            given(upgradeCurrentSessionUseCase)
                .suspendFunction(upgradeCurrentSessionUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withSetUpdateFirebaseTokenFlagResult(result: Either<StorageFailure, Unit>) = apply {
            given(pushTokenRepository)
                .suspendFunction(pushTokenRepository::setUpdateFirebaseTokenFlag)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to getOrRegisterClientUseCase
    }
}
