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

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.auth.verification.FakeSecondFactorVerificationRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
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
            RegisterClientUseCase.RegisterClientParam(
                password = TEST_PASSWORD,
                capabilities = TEST_CAPABILITIES,
                secondFactorVerificationCode = SECOND_FACTOR_CODE
            )
        )

        coVerify {
            arrangement.clientRepository.registerClient(eq(params))
        }.wasInvoked(once)

        coVerify {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }.wasInvoked(exactly = once)
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
            RegisterClientUseCase.RegisterClientParam(
                password = TEST_PASSWORD,
                capabilities = TEST_CAPABILITIES,
                secondFactorVerificationCode = null
            )
        )

        coVerify {
            arrangement.clientRepository.registerClient(
                matches {
                    it.secondFactorVerificationCode == stored2FACode
                }
            )
        }.wasInvoked(once)
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
            RegisterClientUseCase.RegisterClientParam(
                password = TEST_PASSWORD,
                capabilities = TEST_CAPABILITIES,
                secondFactorVerificationCode = passed2FACode
            )
        )

        coVerify {
            arrangement.clientRepository.registerClient(
                matches {
                    it.secondFactorVerificationCode == passed2FACode
                }
            )
        }.wasInvoked(once)
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
            RegisterClientUseCase.RegisterClientParam(
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

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.PasswordAuthRequired>(result)

        coVerify {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueInvalidPassword_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(wrongPasswordFailure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.InvalidPassword>(result)

        coVerify {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToGenericError_whenRegistering_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(genericFailure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

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

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.TooManyClients>(result)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToMissingAuthCode_whenRegistering_thenMissing2FAErrorShouldBeReturned() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuthenticationCode)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(failure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.Missing2FA>(result)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToInvalidAuthCode_whenRegistering_thenInvalid2FAErrorShouldBeReturned() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidAuthenticationCode)

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(failure))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.Invalid2FA>(result)
    }

    @Test
    fun givenRepositoryRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Left(TEST_FAILURE))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        coVerify {
            arrangement.clientRepository.persistClientId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMLSClientRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement(testKaliumDispatcher)
            .withRegisterClient(Either.Right(registeredClient))
            .withRegisterMLSClient(Either.Left(TEST_FAILURE))
            .withSelfCookieLabel(Either.Right(TEST_COOKIE_LABEL))
            .arrange()

        registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        coVerify {
            arrangement.clientRepository.persistClientId(any())
        }.wasNotInvoked()
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

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

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

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

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

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

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

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials.InvalidPassword>(result)

        coVerify {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.preKeyRepository.generateNewLastResortKey()
        }.wasInvoked(exactly = once)
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

        val PRE_KEYS = listOf(PreKeyCrypto(id = 1, encodedData = "1"), PreKeyCrypto(id = 2, encodedData = "2"))
        val LAST_KEY = PreKeyCrypto(id = 99, encodedData = "99")
        val REGISTER_PARAMETERS = RegisterClientParam(
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

        @Mock
        val MLS_CLIENT = mock(MLSClient::class)
        val MLS_PUBLIC_KEY = "public_key".encodeToByteArray()
        val TEST_FAILURE = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))
        const val TEST_COOKIE_LABEL = "cookieLabel"
    }

    @OptIn(DelicateKaliumApi::class)
    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {

        @Mock
        val isAllowedToRegisterMLSClient = mock(IsAllowedToRegisterMLSClientUseCase::class)

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val preKeyRepository = mock(PreKeyRepository::class)

        @Mock
        val keyPackageLimitsProvider = mock(KeyPackageLimitsProvider::class)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val registerMLSClient = mock(RegisterMLSClientUseCase::class)

        val secondFactorVerificationRepository: SecondFactorVerificationRepository = FakeSecondFactorVerificationRepository()

        private val registerClient: RegisterClientUseCase = RegisterClientUseCaseImpl(
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
                }.returns(KEY_PACKAGE_LIMIT)

                coEvery {
                    preKeyRepository.generateNewPreKeys(any(), any())
                }.returns(Either.Right(PRE_KEYS))

                coEvery {
                    preKeyRepository.generateNewLastResortKey()
                }.returns(Either.Right(LAST_KEY))

                coEvery {
                    isAllowedToRegisterMLSClient.invoke()
                }.returns(true)
            }
        }

        suspend fun withSelfUser(selfUser: SelfUser) = apply {
            coEvery {
                userRepository.getSelfUser()
            }.returns(selfUser.right())
        }

        suspend fun withRegisterClient(result: Either<NetworkFailure, Client>) = apply {
            coEvery {
                clientRepository.registerClient(any())
            }.returns(result)
        }

        suspend fun withRegisterMLSClient(result: Either<CoreFailure, RegisterMLSClientResult>) = apply {
            coEvery {
                registerMLSClient.invoke(eq(CLIENT.id))
            }.returns(result)
        }

        suspend fun withGenerateNewPreKeys(result: Either<CoreFailure, List<PreKeyCrypto>>) = apply {
            coEvery {
                preKeyRepository.generateNewPreKeys(any(), any())
            }.returns(result)
        }

        suspend fun withGenerateNewLastKey(result: Either<ProteusFailure, PreKeyCrypto>) = apply {
            coEvery {
                preKeyRepository.generateNewLastResortKey()
            }.returns(result)
        }

        suspend fun withIsAllowedToRegisterMLSClient(result: Boolean) = apply {
            coEvery {
                isAllowedToRegisterMLSClient.invoke()
            }.returns(result)
        }

        suspend fun withUpdateOTRLastPreKeyId(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                preKeyRepository.updateMostRecentPreKeyId(any())
            }.returns(result)
        }

        suspend fun withSelfCookieLabel(result: Either<StorageFailure, String?>) = apply {
            coEvery {
                sessionRepository.cookieLabel(eq(SELF_USER_ID))
            }.returns(result)
        }

        fun arrange() = this to registerClient
    }
}
