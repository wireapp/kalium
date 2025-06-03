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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.framework.TestClient
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetOrRegisterClientUseCaseTest {

    @Test
    fun givenValidClientIsRetained_whenRegisteringAClient_thenDoNotRegisterNewAndReturnPersistedClient() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (arrangement, useCase) = Arrangement()
            .withSyncFeatureConfigResult()
            .withRetainedClientIdResult(Either.Right(clientId))
            .withVerifyExistingClientResult(VerifyExistingClientResult.Success(client))
            .withUpgradeCurrentSessionResult(Either.Right(Unit))
            .withPersistClientIdResult(Either.Right(Unit))
            .withPersistHasConsumableNotifications(false)
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

    @Test
    fun givenInvalidClientIsRetained_whenRegisteringAClient_thenClearDataAndRegisterNewClient() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (arrangement, useCase) = Arrangement()
            .withSyncFeatureConfigResult()
            .withRetainedClientIdResult(Either.Right(clientId))
            .withVerifyExistingClientResult(VerifyExistingClientResult.Failure.ClientNotRegistered)
            .withRegisterClientResult(RegisterClientResult.Success(client))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withClearHasConsumableNotifications(Either.Right(Unit))
            .withUpgradeCurrentSessionResult(Either.Right(Unit))
            .withPersistClientIdResult(Either.Right(Unit))
            .withPersistHasConsumableNotifications(false)
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

    @Test
    fun givenClientNotRetained_whenRegisteringAClient_thenRegisterNewClient() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (arrangement, useCase) = Arrangement()
            .withSyncFeatureConfigResult()
            .withRetainedClientIdResult(Either.Left(CoreFailure.MissingClientRegistration))
            .withRegisterClientResult(RegisterClientResult.Success(client))
            .withUpgradeCurrentSessionResult(Either.Right(Unit))
            .withPersistHasConsumableNotifications(false)
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

    @Test
    fun givenE2EIIsEnabledAndClientNotRetained_whenRegisteringAClient_thenRegisterClientAndReturnE2EIIsRequired() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val userId = TestClient.USER_ID
        val (arrangement, useCase) = Arrangement()
            .withSyncFeatureConfigResult()
            .withRetainedClientIdResult(Either.Left(CoreFailure.MissingClientRegistration))
            .withRegisterClientResult(RegisterClientResult.E2EICertificateRequired(client, userId))
            .withUpgradeCurrentSessionResult(Either.Right(Unit))
            .withPersistClientIdResult(Either.Right(Unit))
            .withSetClientRegistrationBlockedByE2EISucceed()
            .withPersistHasConsumableNotifications(false)
            .arrange()

        val result = useCase.invoke(RegisterClientUseCase.RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.E2EICertificateRequired>(result)
        assertEquals(client, result.client)
        coVerify {
            arrangement.clientRepository.setClientRegistrationBlockedByE2EI()
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

    private class Arrangement {

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val pushTokenRepository = mock(PushTokenRepository::class)

        @Mock
        val logoutRepository = mock(LogoutRepository::class)

        @Mock
        val registerClientUseCase = mock(RegisterClientUseCase::class)

        @Mock
        val clearClientDataUseCase = mock(ClearClientDataUseCase::class)

        @Mock
        val upgradeCurrentSessionUseCase = mock(UpgradeCurrentSessionUseCase::class)

        @Mock
        val syncFeatureConfigsUseCase = mock(SyncFeatureConfigsUseCase::class)

        @Mock
        val verifyExistingClientUseCase = mock(VerifyExistingClientUseCase::class)

        @Mock
        val cachedClientIdClearer = mock(CachedClientIdClearer::class)

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

        suspend fun withSyncFeatureConfigResult(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            coEvery {
                syncFeatureConfigsUseCase.invoke()
            }.returns(Either.Right(Unit))
        }

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

        suspend fun withPersistHasConsumableNotifications(hasConsumableNotifications: Boolean) = apply {
            coEvery {
                clientRepository.persistClientHasConsumableNotifications(hasConsumableNotifications)
            }.returns(Either.Right(Unit))
            return this
        }

        suspend fun withClearHasConsumableNotifications(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                clientRepository.clearClientHasConsumableNotifications()
            }.returns(result)
            return this
        }

        suspend fun withSetClientRegistrationBlockedByE2EISucceed() = apply {
            coEvery {
                clientRepository::setClientRegistrationBlockedByE2EI.invoke()
            }.returns(Unit.right())
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
