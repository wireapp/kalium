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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.auth.verification.FakeSecondFactorVerificationRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParameters
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class RegisterClientUseCaseTest {

    @Test
    fun givenRegistrationParams_whenRegistering_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val params = REGISTER_PARAMETERS

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(TEST_FAILURE))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        registerClient(
            RegisterClientParam(
                password = TEST_PASSWORD,
                capabilities = TEST_CAPABILITIES,
                secondFactorVerificationCode = SECOND_FACTOR_CODE
            )
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.registerClient(params)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }
    }

    @Test
    fun givenStored2FACode_whenRegisteringWithout2FACode_thenTheRepositoryShouldBeCalledWithTheStored2FA() = runTest {
        val stored2FACode = "SomeStored2FACode"

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(TEST_FAILURE))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        arrangement.secondFactorVerificationRepository.storeVerificationCode(SELF_USER_EMAIL, stored2FACode)

        registerClient(
            RegisterClientParam(
                password = TEST_PASSWORD,
                capabilities = TEST_CAPABILITIES,
                secondFactorVerificationCode = null
            )
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.registerClient(
                matches {
                    it.secondFactorVerificationCode == stored2FACode
                }
            )
        }
    }

    @Test
    fun givenStored2FACode_whenRegisteringWith2FACode_thenTheRepositoryShouldBeCalledWithThePassed2FA() = runTest {
        val stored2FACode = "SomeStored2FACode"
        val passed2FACode = "123456"

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(TEST_FAILURE))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        arrangement.secondFactorVerificationRepository.storeVerificationCode(SELF_USER_EMAIL, stored2FACode)

        registerClient(
            RegisterClientParam(
                password = TEST_PASSWORD,
                capabilities = TEST_CAPABILITIES,
                secondFactorVerificationCode = passed2FACode
            )
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.registerClient(
                matches {
                    it.secondFactorVerificationCode == passed2FACode
                }
            )
        }
    }

    @Test
    fun givenStored2FACode_whenRegisteringFailsDueToInvalid2FA_thenTheStored2FAIsCleared() = runTest {
        val stored2FACode = "SomeStored2FACode"
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidAuthenticationCode)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(failure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        arrangement.secondFactorVerificationRepository.storeVerificationCode(SELF_USER_EMAIL, stored2FACode)

        registerClient(
            RegisterClientParam(
                password = TEST_PASSWORD,
                capabilities = TEST_CAPABILITIES,
                secondFactorVerificationCode = null
            )
        )

        val storedResult = arrangement.secondFactorVerificationRepository.getStoredVerificationCode(SELF_USER_EMAIL)
        assertNull(storedResult)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueMissingPassword_whenRegistering_thenPasswordAuthRequiredErrorShouldBeReturned() = runTest {
        val missingPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(missingPasswordFailure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.PasswordAuthRequired>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }
    }

    @Test
    fun givenRepositoryRegistrationFailsDueInvalidPassword_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(wrongPasswordFailure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.InvalidPassword>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToGenericError_whenRegistering_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(genericFailure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertSame(genericFailure, result.genericFailure)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToTooManyClientsRegistered_whenRegistering_thenTooManyClientsErrorShouldBeReturned() = runTest {
        val tooManyClientsFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.tooManyClient)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(tooManyClientsFailure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.TooManyClients>(result)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToMissingAuthCode_whenRegistering_thenMissing2FAErrorShouldBeReturned() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuthenticationCode)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(failure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.Missing2FA>(result)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToInvalidAuthCode_whenRegistering_thenInvalid2FAErrorShouldBeReturned() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidAuthenticationCode)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(failure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.Invalid2FA>(result)
    }

    @Test
    fun givenRepositoryRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(TEST_FAILURE))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.persistClientId(any())
        }
    }

    @Test
    fun givenMLSClientRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Right(registeredClient))
            .withRegisterMLSClient(Either.Left(TEST_FAILURE))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.persistClientId(any())
        }
    }

    @Test
    fun givenRegisteringSucceedsAndPersistingClientIdSucceeds_whenRegistering_thenSuccessShouldBePropagated() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Right(registeredClient))
            .withRegisterMLSClient(Either.Right(RegisterMLSClientResult.Success))
            .withUpdateOTRLastPreKeyId(Either.Right(Unit))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    @Test
    fun givenRegistering_whenAsyncNotificationsAllowed_thenConsumableNotificationCapabilityShouldBeAdded() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Right(registeredClient))
            .withRegisterMLSClient(Either.Right(RegisterMLSClientResult.Success))
            .withUpdateOTRLastPreKeyId(Either.Right(Unit))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .withIsAllowedToUseAsyncNotifications(true)
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, null))

        verifySuspend {
            arrangement.clientRepository.registerClient(matches {
                it.capabilities?.contains(ClientCapability.ConsumableNotifications) == true
            })
        }
        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    @Test
    fun givenRegistering_whenAsyncNotificationsIsNOTAllowed_thenConsumableNotificationCapabilityShouldNOTBeAdded() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Right(registeredClient))
            .withRegisterMLSClient(Either.Right(RegisterMLSClientResult.Success))
            .withUpdateOTRLastPreKeyId(Either.Right(Unit))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .withIsAllowedToUseAsyncNotifications(false)
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, null))

        verifySuspend {
            arrangement.clientRepository.registerClient(matches {
                it.capabilities?.contains(ClientCapability.ConsumableNotifications) == false
            })
        }
        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    @Test
    fun givenProteusClient_whenNewPreKeysThrowException_thenReturnProteusFailure() = runTest {
        val failure = ProteusFailure(ProteusException("why are we still here just to suffer", 55))

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withGenerateNewPreKeys(Either.Left(failure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(failure, result.genericFailure)
    }

    @Test
    fun givenProteusClient_whenNewLastPreKeyThrowException_thenReturnProteusFailure() = runTest {
        val failure = ProteusFailure(ProteusException("why are we still here just to suffer", 55))

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withGenerateNewLastKey(Either.Left(failure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(failure, result.genericFailure)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueBadRequest_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val badRequestFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(badRequestFailure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.InvalidPassword>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }
    }

    // mls returns e2ei is required
    // make sure we invoked the team settings fetched
    // finalizing the client registration

    private companion object {
        const val KEY_PACKAGE_LIMIT = 100
        const val TEST_PASSWORD = "password"
        const val SECOND_FACTOR_CODE = "123456"
        const val SELF_USER_EMAIL = "user@example.org"
        val SELF_USER = TestUser.SELF.copy(email = SELF_USER_EMAIL)
        val SELF_USER_ID = SELF_USER.id
        val TEST_CAPABILITIES: List<ClientCapability> = listOf(
            ClientCapability.LegalHoldImplicitConsent
        )

        val PRE_KEYS = listOf(PreKeyCrypto(id = 1, pkb = "1"), PreKeyCrypto(id = 2, pkb = "2"))
        val LAST_KEY = PreKeyCrypto(id = 99, pkb = "99")
        val REGISTER_PARAMETERS = RegisterClientParameters(
            password = TEST_PASSWORD,
            preKeys = PRE_KEYS,
            lastKey = LAST_KEY,
            capabilities = TEST_CAPABILITIES,
            deviceType = null,
            label = null,
            model = null,
            clientType = null,
            cookieLabel = "cookieLabel",
            secondFactorVerificationCode = SECOND_FACTOR_CODE
        )
        val CLIENT = TestClient.CLIENT
        val MLS_CLIENT: MLSClient = mock(mode = MockMode.autoUnit)
        val MLS_PUBLIC_KEY = "public_key".encodeToByteArray()
        val TEST_FAILURE = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))
        const val TEST_COOKIE_LABEL = "cookieLabel"
    }

    @OptIn(DelicateKaliumApi::class)
    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
        val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase = mock(mode = MockMode.autoUnit)
        val clientRepository: ClientRepository = mock(mode = MockMode.autoUnit)
        val preKeyRepository: PreKeyRepository = mock(mode = MockMode.autoUnit)
        val keyPackageLimitsProvider: KeyPackageLimitsProvider = mock(mode = MockMode.autoUnit)
        val sessionRepository: SessionRepository = mock(mode = MockMode.autoUnit)
        val userRepository: UserRepository = mock(mode = MockMode.autoUnit)
        val registerMLSClient: RegisterMLSClientUseCase = mock(mode = MockMode.autoUnit)
        val isAllowedToUseAsyncNotifications: IsAllowedToUseAsyncNotificationsUseCase = mock(mode = MockMode.autoUnit)

        val secondFactorVerificationRepository: SecondFactorVerificationRepository = FakeSecondFactorVerificationRepository()

        private val registerClient: RegisterClientUseCase = RegisterClientUseCaseImpl(
            isAllowedToUseAsyncNotifications,
            isAllowedToRegisterMLSClient,
            clientRepository,
            preKeyRepository,
            sessionRepository,
            SELF_USER_ID,
            userRepository,
            secondFactorVerificationRepository,
            registerMLSClient,
            dispatcher
        )

        init {
            runBlocking {
                withSelfUser(SELF_USER)
                every {
                    keyPackageLimitsProvider.refillAmount()
                } returns KEY_PACKAGE_LIMIT

                everySuspend {
                    preKeyRepository.generateNewPreKeys(any(), any())
                } returns Either.Right(PRE_KEYS)

                everySuspend {
                    preKeyRepository.generateNewLastResortKey()
                } returns Either.Right(LAST_KEY)

                everySuspend {
                    isAllowedToRegisterMLSClient.invoke()
                } returns true

                withIsAllowedToUseAsyncNotifications(false)
            }
        }

        fun withIsAllowedToUseAsyncNotifications(isAllowed: Boolean = false) = apply {
            everySuspend {
                isAllowedToUseAsyncNotifications()
            } returns isAllowed
        }

        fun withSelfUser(selfUser: SelfUser) = apply {
            everySuspend {
                userRepository.getSelfUser()
            } returns selfUser.right()
        }

        fun withRegisterClient(result: Either<NetworkFailure, Client>) = apply {
            everySuspend {
                clientRepository.registerClient(any())
            } returns result
        }

        fun withRegisterMLSClient(result: Either<CoreFailure, RegisterMLSClientResult>) = apply {
            everySuspend {
                registerMLSClient.invoke(CLIENT.id)
            } returns result
        }

        fun withGenerateNewPreKeys(result: Either<CoreFailure, List<PreKeyCrypto>>) = apply {
            everySuspend {
                preKeyRepository.generateNewPreKeys(any(), any())
            } returns result
        }

        fun withGenerateNewLastKey(result: Either<ProteusFailure, PreKeyCrypto>) = apply {
            everySuspend {
                preKeyRepository.generateNewLastResortKey()
            } returns result
        }

        fun withIsAllowedToRegisterMLSClient(result: Boolean) = apply {
            everySuspend {
                isAllowedToRegisterMLSClient.invoke()
            } returns result
        }

        fun withUpdateOTRLastPreKeyId(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                preKeyRepository.updateMostRecentPreKeyId(any())
            } returns result
        }

        fun withSelfCookieLabel(result: Either<StorageFailure, String?>) = apply {
            everySuspend {
                sessionRepository.cookieLabel(SELF_USER_ID)
            } returns result
        }

        fun arrange() = this to registerClient
    }
}
