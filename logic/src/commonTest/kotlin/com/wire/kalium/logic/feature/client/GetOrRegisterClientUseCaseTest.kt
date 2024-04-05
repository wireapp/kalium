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
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.configure
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetOrRegisterClientUseCaseTest {

    // todo: fix later
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
        coVerify {
            arrangement.registerClientUseCase.invoke(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.upgradeCurrentSessionUseCase.invoke(eq(clientId))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clientRepository.persistClientId(eq(clientId))
        }.wasInvoked(exactly = once)
    }

    // todo: fix later
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
        verify {
            arrangement.cachedClientIdClearer.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clearClientDataUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.logoutRepository.clearClientRelatedLocalMetadata()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clientRepository.clearRetainedClientId()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.registerClientUseCase.invoke(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.upgradeCurrentSessionUseCase.invoke(eq(clientId))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clientRepository.persistClientId(eq(clientId))
        }.wasInvoked(exactly = once)
    }

    // todo: fix later
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
        coVerify {
            arrangement.registerClientUseCase.invoke(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.upgradeCurrentSessionUseCase.invoke(eq(clientId))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clientRepository.persistClientId(eq(clientId))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val pushTokenRepository = mock(classOf<PushTokenRepository>())

        @Mock
        val logoutRepository = configure(mock(classOf<LogoutRepository>())) { }

        @Mock
        val registerClientUseCase = mock(classOf<RegisterClientUseCase>())

        @Mock
        val clearClientDataUseCase = configure(mock(classOf<ClearClientDataUseCase>())) { }

        @Mock
        val upgradeCurrentSessionUseCase = mock(classOf<UpgradeCurrentSessionUseCase>())

        @Mock
        val syncFeatureConfigsUseCase = mock(classOf<SyncFeatureConfigsUseCase>())

        @Mock
        val verifyExistingClientUseCase = mock(classOf<VerifyExistingClientUseCase>())

        @Mock
        val cachedClientIdClearer = configure(mock(classOf<CachedClientIdClearer>())) { }

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

        suspend fun withRetainedClientIdResult(result: Either<CoreFailure, ClientId>) = apply {
            coEvery {
                clientRepository.retainedClientId()
            }.returns(result)
        }

        suspend fun withRegisterClientResult(result: RegisterClientResult) = apply {
            coEvery {
                registerClientUseCase.invoke(any())
            }.returns(result)
        }

        suspend fun withClearRetainedClientIdResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                clientRepository.clearRetainedClientId()
            }.returns(result)
        }

        suspend fun withPersistClientIdResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                clientRepository.persistClientId(any())
            }.returns(result)
        }

        suspend fun withVerifyExistingClientResult(result: VerifyExistingClientResult) = apply {
            coEvery {
                verifyExistingClientUseCase.invoke(any())
            }.returns(result)
        }

        suspend fun withUpgradeCurrentSessionResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                upgradeCurrentSessionUseCase.invoke(any())
            }.returns(result)
        }

        suspend fun withSetUpdateFirebaseTokenFlagResult(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                pushTokenRepository.setUpdateFirebaseTokenFlag(any())
            }.returns(result)
        }

        fun arrange() = this to getOrRegisterClientUseCase
    }
}
