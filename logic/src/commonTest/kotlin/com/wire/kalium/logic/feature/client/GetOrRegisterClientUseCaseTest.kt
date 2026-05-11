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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.mokkery.verify
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

        val result = useCase.invoke(RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verifySuspend(VerifyMode.not) {
            arrangement.registerClientUseCase.invoke(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.upgradeCurrentSessionUseCase.invoke(eq(clientId))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.persistClientId(eq(clientId))
        }
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

        val result = useCase.invoke(RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verify(VerifyMode.exactly(1)) {
            arrangement.cachedClientIdClearer.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.logoutRepository.clearClientRelatedLocalMetadata()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.clearRetainedClientId()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.registerClientUseCase.invoke(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.upgradeCurrentSessionUseCase.invoke(eq(clientId))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.persistClientId(eq(clientId))
        }
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

        val result = useCase.invoke(RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.registerClientUseCase.invoke(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.upgradeCurrentSessionUseCase.invoke(eq(clientId))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.persistClientId(eq(clientId))
        }
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

        val result = useCase.invoke(RegisterClientParam("", listOf()))

        assertIs<RegisterClientResult.E2EICertificateRequired>(result)
        assertEquals(client, result.client)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.setClientRegistrationBlockedByE2EI()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.registerClientUseCase.invoke(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.upgradeCurrentSessionUseCase.invoke(eq(clientId))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.persistClientId(eq(clientId))
        }
    }

    private class Arrangement {
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val pushTokenRepository = mock<PushTokenRepository>(mode = MockMode.autoUnit)
        val logoutRepository = mock<LogoutRepository>(mode = MockMode.autoUnit)
        val registerClientUseCase = mock<RegisterClientUseCase>(mode = MockMode.autoUnit)
        val clearClientDataUseCase = mock<ClearClientDataUseCase>(mode = MockMode.autoUnit)
        val upgradeCurrentSessionUseCase = mock<UpgradeCurrentSessionUseCase>(mode = MockMode.autoUnit)
        val syncFeatureConfigsUseCase = mock<SyncFeatureConfigsUseCase>(mode = MockMode.autoUnit)
        val verifyExistingClientUseCase = mock<VerifyExistingClientUseCase>(mode = MockMode.autoUnit)
        val cachedClientIdClearer = mock<CachedClientIdClearer>(mode = MockMode.autoUnit)

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
            everySuspend {
                syncFeatureConfigsUseCase.invoke()
            } returns Either.Right(Unit)
        }

        suspend fun withRetainedClientIdResult(result: Either<CoreFailure, ClientId>) = apply {
            everySuspend {
                clientRepository.retainedClientId()
            } returns result
        }

        suspend fun withRegisterClientResult(result: RegisterClientResult) = apply {
            everySuspend {
                registerClientUseCase.invoke(any())
            } returns result
        }

        suspend fun withClearRetainedClientIdResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                clientRepository.clearRetainedClientId()
            } returns result
        }

        suspend fun withPersistClientIdResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                clientRepository.persistClientId(any())
            } returns result
        }

        suspend fun withPersistHasConsumableNotifications(hasConsumableNotifications: Boolean) = apply {
            everySuspend {
                clientRepository.persistClientHasConsumableNotifications(hasConsumableNotifications)
            } returns Either.Right(Unit)
            return this
        }

        suspend fun withClearHasConsumableNotifications(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                clientRepository.clearClientHasConsumableNotifications()
            } returns result
            return this
        }

        suspend fun withSetClientRegistrationBlockedByE2EISucceed() = apply {
            everySuspend {
                clientRepository.setClientRegistrationBlockedByE2EI()
            } returns Unit.right()
        }

        suspend fun withVerifyExistingClientResult(result: VerifyExistingClientResult) = apply {
            everySuspend {
                verifyExistingClientUseCase.invoke(any())
            } returns result
        }

        suspend fun withUpgradeCurrentSessionResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                upgradeCurrentSessionUseCase.invoke(any())
            } returns result
        }

        suspend fun withSetUpdateFirebaseTokenFlagResult(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                pushTokenRepository.setUpdateFirebaseTokenFlag(any())
            } returns result
        }

        fun arrange() = apply {
            every { cachedClientIdClearer.invoke() } returns Unit
            everySuspend { clearClientDataUseCase.invoke() } returns Unit
            everySuspend { logoutRepository.clearClientRelatedLocalMetadata() } returns Unit
        }.let { this to getOrRegisterClientUseCase }
    }
}
