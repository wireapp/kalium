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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.cryptography.AcmeChallenge
import com.wire.kalium.cryptography.AcmeDirectory
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.cryptography.NewAcmeOrder
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.ACME_CHALLENGE
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.E2EI_TEAM_SETTINGS
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.RANDOM_ACCESS_TOKEN
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.RANDOM_ID_TOKEN
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.RANDOM_NONCE
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.RANDOM_URL
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.REFRESH_TOKEN
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryTest.Arrangement.Companion.TEST_FAILURE
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEAuthorizationResponse
import com.wire.kalium.network.api.base.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import com.wire.kalium.network.api.base.unbound.acme.DtoAuthorizationChallengeType
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.DateTimeUtil
import io.ktor.http.Url
import io.mockative.Mock
import io.mockative.any
import io.mockative.anyInstanceOf
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.time
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class E2EIRepositoryTest {
    @Test
    fun givenGettingE2EITeamSettingsFails_whenLoadAcmeDirectories_thenItFail() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Left(StorageFailure.DataNotFound))
            .withAcmeDirectoriesApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withE2EIClientLoadDirectoriesSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.loadACMEDirectories()

        // Then
        result.shouldFail()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::getE2EISettings)
            .with()
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::getACMEDirectories)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::directoryResponse)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenACMEDirectoriesApiSucceed_whenLoadAcmeDirectories_thenItSucceed() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS))
            .withAcmeDirectoriesApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withE2EIClientLoadDirectoriesSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.loadACMEDirectories()

        // Then
        result.shouldSucceed()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::getE2EISettings)
            .with()
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::getACMEDirectories)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::directoryResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenACMEDirectoriesApiFails_whenLoadAcmeDirectories_thenItFail() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS))
            .withAcmeDirectoriesApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withE2EIClientLoadDirectoriesSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.loadACMEDirectories()

        // Then
        result.shouldFail()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::getE2EISettings)
            .with()
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::getACMEDirectories)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::directoryResponse)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenSendAcmeRequestSucceed_whenCallingCreateNewAccount_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewAccountSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.createNewAccount(RANDOM_NONCE, RANDOM_URL)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewAccountRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setAccountResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSendAcmeRequestFails_whenCallingCreateNewAccount_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewAccountSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.createNewAccount(RANDOM_NONCE, RANDOM_URL)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewAccountRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setAccountResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendAcmeRequestSucceed_whenCallingCreateNewOrder_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewOrderSuccessful()
            .withSetOrderResponseSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.createNewOrder(RANDOM_NONCE, RANDOM_URL)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewOrderRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setOrderResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSendAcmeRequestFails_whenCallingCreateNewOrder_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewOrderSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.createNewOrder(RANDOM_NONCE, RANDOM_URL)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewOrderRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setOrderResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendAuthorizationRequestSucceed_whenCallingCreateAuthz_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withGetNewAuthzRequestSuccessful()
            .withSendAuthorizationRequestSucceed(url = RANDOM_URL, DtoAuthorizationChallengeType.DPoP)
            .withSendAcmeRequestApiSucceed()
            .withSetAuthzResponseSuccessful()
            .withSendAcmeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.createAuthorization(RANDOM_NONCE, RANDOM_URL)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::getNewAuthzRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendAuthorizationRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::setAuthzResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSendAuthorizationRequestFails_whenCallingCreateAuthz_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withGetNewAuthzRequestSuccessful()
            .withSendAuthorizationRequestFails()
            .withSendAcmeRequestApiSucceed()
            .withSetAuthzResponseSuccessful()
            .withSendAcmeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.createAuthorization(RANDOM_NONCE, RANDOM_URL)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::getNewAuthzRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendAuthorizationRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::setAuthzResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendAuthorizationRequestFails_whenCallingGetAuthorizations_thenItFail() = runTest {
        val authorizationsUrls = listOf(RANDOM_URL, RANDOM_URL)
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAuthorizationRequestFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewAuthzRequestSuccessful()
            .withSetAuthzResponseSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.getAuthorizations(RANDOM_NONCE, authorizationsUrls)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::getNewAuthzRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendAuthorizationRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::setAuthzResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendAuthorizationRequestSucceed_whenCallingGetAuthorizations_thenItSucceed() = runTest {
        val authorizationsUrls = listOf("$RANDOM_URL/oidc", "$RANDOM_URL/dpop")
        val expected = Arrangement.AUTHORIZATIONS_RESULT
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAuthorizationRequestSucceed(url = authorizationsUrls[0], DtoAuthorizationChallengeType.DPoP)
            .withSendAuthorizationRequestSucceed(url = authorizationsUrls[1], DtoAuthorizationChallengeType.OIDC)
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewAuthzRequestSuccessful()
            .withSetAuthzResponseSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.getAuthorizations(RANDOM_NONCE, authorizationsUrls)

        // Then
        result.shouldSucceed()

        assertIs<AuthorizationResult>(result.value)

        assertEquals(expected, result.value as AuthorizationResult)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::getNewAuthzRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(authorizationsUrls.size.time)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendAuthorizationRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(authorizationsUrls.size.time)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::setAuthzResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(authorizationsUrls.size.time)
    }

    @Test
    fun givenDpopChallengeRequestSucceed_whenCallingValidateDPoPChallenge_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withGetCoreCryptoSuccessful()
            .withSendChallengeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewDpopChallengeRequest()
            .arrange()

        // When
        val result = e2eiRepository.validateDPoPChallenge(RANDOM_ACCESS_TOKEN, RANDOM_NONCE, ACME_CHALLENGE)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewDpopChallengeRequest)
            .with(anyInstanceOf(String::class), anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendChallengeRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setDPoPChallengeResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenDpopChallengeRequestFails_whenCallingValidateDPoPChallenge_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendChallengeRequestApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewDpopChallengeRequest()
            .arrange()

        // When
        val result = e2eiRepository.validateDPoPChallenge(RANDOM_ACCESS_TOKEN, RANDOM_NONCE, ACME_CHALLENGE)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewDpopChallengeRequest)
            .with(anyInstanceOf(String::class), anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendChallengeRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setOIDCChallengeResponse)
            .with(anyInstanceOf(CoreCryptoCentral::class), anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenOIDCChallengeRequestSucceed_whenCallingValidateDPoPChallenge_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withGetCoreCryptoSuccessful()
            .withSendChallengeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewOidcChallengeRequest()
            .arrange()

        // When
        val result = e2eiRepository.validateOIDCChallenge(RANDOM_ID_TOKEN, REFRESH_TOKEN, RANDOM_NONCE, ACME_CHALLENGE)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewOidcChallengeRequest)
            .with(anyInstanceOf(String::class), anyInstanceOf(String::class), anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendChallengeRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setOIDCChallengeResponse)
            .with(anyInstanceOf(CoreCryptoCentral::class), anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenOIDCChallengeRequestFails_whenCallingValidateDPoPChallenge_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withGetCoreCryptoSuccessful()
            .withSendChallengeRequestApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewOidcChallengeRequest()
            .arrange()

        // When
        val result = e2eiRepository.validateOIDCChallenge(RANDOM_ID_TOKEN, REFRESH_TOKEN, RANDOM_NONCE, ACME_CHALLENGE)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::getNewOidcChallengeRequest)
            .with(anyInstanceOf(String::class), anyInstanceOf(String::class), anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendChallengeRequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::setOIDCChallengeResponse)
            .with(anyInstanceOf(CoreCryptoCentral::class), anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendAcmeRequestSucceed_whenCallingCheckOrderRequest_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withCheckOrderRequestSuccessful()
            .withCheckOrderResponseSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.checkOrderRequest(RANDOM_URL, RANDOM_NONCE)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::checkOrderRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::checkOrderResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSendAcmeRequestFails_whenCallingCheckOrderRequest_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withCheckOrderRequestSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.checkOrderRequest(RANDOM_URL, RANDOM_NONCE)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::checkOrderRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::checkOrderResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendAcmeRequestSucceed_whenCallingFinalize_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withFinalizeRequestSuccessful()
            .withFinalizeResponseSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.finalize(RANDOM_URL, RANDOM_NONCE)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .function(arrangement.e2eiClient::finalizeRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::finalizeResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenSendAcmeRequestFails_whenCallingFinalize_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withFinalizeRequestSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.finalize(RANDOM_URL, RANDOM_NONCE)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::finalizeRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::finalizeResponse)
            .with(anyInstanceOf(ByteArray::class))
            .wasNotInvoked()
    }

    @Test
    fun givenSendAcmeRequestSucceed_whenCallingCertificateRequest_thenItSucceed() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiSucceed()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withCertificateRequestSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.certificateRequest(RANDOM_URL, RANDOM_NONCE)

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::certificateRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)
    }

    @Test
    fun givenSendAcmeRequestFails_whenCallingCertificateRequest_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSendAcmeRequestApiFails()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withCertificateRequestSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.certificateRequest(RANDOM_URL, RANDOM_NONCE)

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClient)
            .suspendFunction(arrangement.e2eiClient::certificateRequest)
            .with(anyInstanceOf(String::class))
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::sendACMERequest)
            .with(anyInstanceOf(String::class), any())
            .wasInvoked(once)
    }

    @Test
    fun givenCertificate_whenCallingRotateKeysAndMigrateConversation_thenItSuccess() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withCurrentClientIdProviderSuccessful()
            .withGetE2EIClientSuccessful()
            .withRotateKeysAndMigrateConversationsReturns(Either.Right(Unit))
            .arrange()

        // When
        val result = e2eiRepository.rotateKeysAndMigrateConversations("")

        // Then
        result.shouldSucceed()

        verify(arrangement.e2eiClientProvider)
            .suspendFunction(arrangement.e2eiClientProvider::getE2EIClient)
            .with(anything())
            .wasInvoked(once)

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasInvoked(once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::rotateKeysAndMigrateConversations)
            .with(anything(), anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenCertificate_whenCallingRotateKeysAndMigrateConversationFails_thenReturnFailure() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withCurrentClientIdProviderSuccessful()
            .withGetE2EIClientSuccessful()
            .withRotateKeysAndMigrateConversationsReturns(TEST_FAILURE)
            .arrange()

        // When
        val result = e2eiRepository.rotateKeysAndMigrateConversations("")

        // Then
        result.shouldFail()

        verify(arrangement.e2eiClientProvider)
            .suspendFunction(arrangement.e2eiClientProvider::getE2EIClient)
            .with(anything())
            .wasInvoked(once)

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasInvoked(once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::rotateKeysAndMigrateConversations)
            .with(anything(), anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenGettingE2EITeamSettingsFails_whenFetchACMECertificates_thenItFail() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Left(StorageFailure.DataNotFound))
            .withAcmeFederationApiFails()
            .withGetMLSClientSuccessful()
            .withRegisterIntermediateCABag()
            .arrange()

        // When
        val result = e2eiRepository.fetchFederationCertificates()

        // Then
        result.shouldFail()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::getE2EISettings)
            .with()
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::getACMEFederation)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.coreCryptoCentral)
            .suspendFunction(arrangement.coreCryptoCentral::registerIntermediateCa)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenACMEFederationApiSucceed_whenFetchACMECertificates_thenItSucceed() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS))
            .withAcmeFederationApiSucceed()
            .withCurrentClientIdProviderSuccessful()
            .withGetCoreCryptoSuccessful()
            .withRegisterIntermediateCABag()
            .arrange()

        // When
        val result = e2eiRepository.fetchFederationCertificates()

        // Then
        result.shouldSucceed()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::getE2EISettings)
            .with()
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::getACMEFederation)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.coreCryptoCentral)
            .suspendFunction(arrangement.coreCryptoCentral::registerIntermediateCa)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenGettingE2EITeamSettingsFails_whenFetchATrustAnchors_thenItFail() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Left(StorageFailure.DataNotFound))
            .withFetchAcmeTrustAnchorsApiFails()
            .withGetMLSClientSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.fetchAndSetTrustAnchors()

        // Then
        result.shouldFail()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::getE2EISettings)
            .with()
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::getACMEFederation)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.coreCryptoCentral)
            .suspendFunction(arrangement.coreCryptoCentral::registerIntermediateCa)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenACMETrustAnchorsApiSucceed_whenFetchACMETrustAnchors_thenItSucceed() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(discoverUrl = RANDOM_URL)))
            .withFetchAcmeTrustAnchorsApiSucceed()
            .withCurrentClientIdProviderSuccessful()
            .withCurrentClientIdProviderSuccessful()
            .withGetCoreCryptoSuccessful()
            .withRegisterTrustAnchors()
            .arrange()

        // When
        val result = e2eiRepository.fetchAndSetTrustAnchors()

        // Then
        result.shouldSucceed()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::getE2EISettings)
            .with()
            .wasInvoked(once)

        verify(arrangement.acmeApi)
            .suspendFunction(arrangement.acmeApi::getTrustAnchors)
            .with(eq(Url(RANDOM_URL)))
            .wasInvoked(once)

        verify(arrangement.coreCryptoCentral)
            .suspendFunction(arrangement.coreCryptoCentral::registerTrustAnchors)
            .with(eq(Arrangement.RANDOM_BYTE_ARRAY.decodeToString()))
            .wasInvoked(once)
    }

    @Test
    fun givenE2EIIsDisabled_whenCallingDiscoveryUrl_thenItFailWithDisabled() {
        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EISettings(false, null, Instant.DISTANT_FUTURE)))
            .arrange()

        e2eiRepository.discoveryUrl().shouldFail {
            assertIs<E2EIFailure.Disabled>(it)
        }

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::getE2EISettings)
            .wasInvoked(once)
    }

    @Test
    fun givenE2EIIsEnabledAndDiscoveryUrlIsNull_whenCallingDiscoveryUrl_thenItFailWithMissingDiscoveryUrl() {
        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EISettings(true, null, Instant.DISTANT_FUTURE)))
            .arrange()

        e2eiRepository.discoveryUrl().shouldFail {
            assertIs<E2EIFailure.MissingDiscoveryUrl>(it)
        }

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::getE2EISettings)
            .wasInvoked(once)
    }

    @Test
    fun givenE2EIIsEnabledAndDiscoveryUrlIsNotNull_whenCallingDiscoveryUrl_thenItSucceed() {
        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EISettings(true, RANDOM_URL, Instant.DISTANT_FUTURE)))
            .arrange()

        e2eiRepository.discoveryUrl().shouldSucceed {
            assertEquals(Url(RANDOM_URL), it)
        }

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::getE2EISettings)
            .wasInvoked(once)
    }

    private class Arrangement {

        fun withGetE2EIClientSuccessful() = apply {
            given(e2eiClientProvider)
                .suspendFunction(e2eiClientProvider::getE2EIClient)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(e2eiClient))
        }

        fun withGetCoreCryptoSuccessful() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getCoreCrypto)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(coreCryptoCentral))
        }

        fun withE2EIClientLoadDirectoriesSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::directoryResponse)
                .whenInvokedWith(anything())
                .thenReturn(ACME_DIRECTORIES)
        }

        fun withGetNewAccountSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::getNewAccountRequest)
                .whenInvokedWith(anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withGetNewOrderSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::getNewOrderRequest)
                .whenInvokedWith(anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withGetNewAuthzRequestSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::getNewAuthzRequest)
                .whenInvokedWith(anything(), anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withCheckOrderRequestSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::checkOrderRequest)
                .whenInvokedWith(anything(), anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withFinalizeRequestSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::finalizeRequest)
                .whenInvokedWith(anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withCertificateRequestSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::certificateRequest)
                .whenInvokedWith(anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withRotateKeysAndMigrateConversationsReturns(result: Either<E2EIFailure, Unit>) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::rotateKeysAndMigrateConversations)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withCurrentClientIdProviderSuccessful() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestClient.CLIENT_ID))
        }

        fun withFinalizeResponseSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::finalizeResponse)
                .whenInvokedWith(anything())
                .thenReturn("")
        }

        fun withCheckOrderResponseSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::checkOrderResponse)
                .whenInvokedWith(anything())
                .thenReturn("")
        }

        fun withGetNewDpopChallengeRequest() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::getNewDpopChallengeRequest)
                .whenInvokedWith(anything(), anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withGetNewOidcChallengeRequest() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::getNewOidcChallengeRequest)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(RANDOM_BYTE_ARRAY)
        }

        fun withSetOrderResponseSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::setOrderResponse)
                .whenInvokedWith(anything())
                .thenReturn(ACME_ORDER)
        }

        fun withSetAuthzResponseSuccessful() = apply {
            given(e2eiClient)
                .suspendFunction(e2eiClient::setAuthzResponse)
                .whenInvokedWith(anything())
                .thenReturn(OIDC_AUTHZ)
        }

        fun withGetMLSClientSuccessful() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(mlsClient))
        }

        fun withGettingE2EISettingsReturns(result: Either<StorageFailure, E2EISettings>) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::getE2EISettings)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withAcmeDirectoriesApiSucceed() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::getACMEDirectories)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(ACME_DIRECTORIES_RESPONSE, mapOf(), 200))
        }

        fun withAcmeDirectoriesApiFails() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::getACMEDirectories).whenInvokedWith(any())
                .thenReturn(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        fun withSendAcmeRequestApiSucceed() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::sendACMERequest)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(ACME_REQUEST_RESPONSE, mapOf(), 200))
        }

        fun withSendAcmeRequestApiFails() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::sendACMERequest)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        fun withSendAuthorizationRequestSucceed(url: String, challengeType: DtoAuthorizationChallengeType) = apply {
            given(acmeApi).suspendFunction(acmeApi::sendAuthorizationRequest).whenInvokedWith(eq(url), any())
                .thenReturn(
                    NetworkResponse.Success(
                        ACME_AUTHORIZATION_RESPONSE.copy(challengeType = challengeType),
                        headers = HEADERS,
                        200
                    )
                )
        }

        fun withSendAuthorizationRequestFails() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::sendAuthorizationRequest)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        fun withSendChallengeRequestApiSucceed() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::sendChallengeRequest)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(ACME_CHALLENGE_RESPONSE, mapOf(), 200))
        }

        fun withSendChallengeRequestApiFails() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::sendChallengeRequest)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        fun withAcmeFederationApiSucceed() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::getACMEFederation)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success("", mapOf(), 200))
        }

        fun withAcmeFederationApiFails() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::getACMEFederation)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        fun withFetchAcmeTrustAnchorsApiFails() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::getTrustAnchors)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        fun withFetchAcmeTrustAnchorsApiSucceed() = apply {
            given(acmeApi)
                .suspendFunction(acmeApi::getTrustAnchors)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(RANDOM_BYTE_ARRAY, mapOf(), 200))
        }

        fun withRegisterIntermediateCABag() = apply {
            given(coreCryptoCentral)
                .suspendFunction(coreCryptoCentral::registerIntermediateCa)
                .whenInvokedWith(any())
                .thenDoNothing()
        }

        fun withRegisterTrustAnchors() = apply {
            given(coreCryptoCentral)
                .suspendFunction(coreCryptoCentral::registerTrustAnchors)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        @Mock
        val e2eiApi: E2EIApi = mock(classOf<E2EIApi>())

        @Mock
        val acmeApi: ACMEApi = mock(classOf<ACMEApi>())

        @Mock
        val e2eiClientProvider: E2EIClientProvider = mock(classOf<E2EIClientProvider>())

        @Mock
        val e2eiClient = mock(classOf<E2EIClient>())

        @Mock
        val coreCryptoCentral = mock(classOf<CoreCryptoCentral>())

        @Mock
        val mlsClientProvider: MLSClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val currentClientIdProvider: CurrentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun arrange() =
            this to E2EIRepositoryImpl(
                e2eiApi,
                acmeApi,
                e2eiClientProvider,
                mlsClientProvider,
                currentClientIdProvider,
                mlsConversationRepository,
                userConfigRepository
            )

        companion object {
            val TEST_FAILURE = Either.Left(E2EIFailure.Generic(Exception("an error")))
            val INVALID_REQUEST_ERROR = KaliumException.InvalidRequestError(ErrorResponse(405, "", ""))
            val RANDOM_BYTE_ARRAY = "random-value".encodeToByteArray()
            val RANDOM_NONCE = Nonce("xxxxx")
            val REFRESH_TOKEN = "YRjxLpsjRqL7zYuKstXogqioA_P3Z4fiEuga0NCVRcDSc8cy_9msxg"
            val RANDOM_ACCESS_TOKEN = "xxxxx"
            val RANDOM_ID_TOKEN = "xxxxx"
            val RANDOM_URL = "https://random.rn"

            val ACME_BASE_URL = "https://balderdash.hogwash.work:9000"

            val ACME_DIRECTORIES_RESPONSE = AcmeDirectoriesResponse(
                newNonce = "$ACME_BASE_URL/acme/wire/new-nonce",
                newAccount = "$ACME_BASE_URL/acme/wire/new-account",
                newOrder = "$ACME_BASE_URL/acme/wire/new-order",
                revokeCert = "$ACME_BASE_URL/acme/wire/revoke-cert",
                keyChange = "$ACME_BASE_URL/acme/wire/key-change"
            )

            val ACME_DIRECTORIES = AcmeDirectory(
                newNonce = "$ACME_BASE_URL/acme/wire/new-nonce",
                newAccount = "$ACME_BASE_URL/acme/wire/new-account",
                newOrder = "$ACME_BASE_URL/acme/wire/new-order"
            )

            val ACME_REQUEST_RESPONSE = ACMEResponse(
                nonce = RANDOM_NONCE.value,
                location = RANDOM_URL,
                response = RANDOM_BYTE_ARRAY
            )

            val ACME_ORDER = NewAcmeOrder(
                delegate = RANDOM_BYTE_ARRAY,
                authorizations = emptyList()
            )

            val ACME_CHALLENGE = AcmeChallenge(
                delegate = RANDOM_BYTE_ARRAY,
                url = RANDOM_URL,
                target = RANDOM_URL
            )

            val OIDC_AUTHZ = NewAcmeAuthz(
                identifier = "identifier",
                keyAuth = "keyauth",
                challenge = ACME_CHALLENGE
            )

            val DPOP_AUTHZ = NewAcmeAuthz(
                identifier = "identifier",
                keyAuth = "keyauth",
                challenge = ACME_CHALLENGE
            )

            val ACME_CHALLENGE_RESPONSE = ChallengeResponse(
                type = "type",
                url = "url",
                status = "status",
                token = "token",
                target = "target",
                nonce = "nonce"
            )

            val ACME_AUTHORIZATION_RESPONSE = ACMEAuthorizationResponse(
                nonce = RANDOM_NONCE.value,
                location = RANDOM_URL,
                response = RANDOM_BYTE_ARRAY,
                challengeType = DtoAuthorizationChallengeType.DPoP
            )

            val AUTHORIZATIONS_RESULT = AuthorizationResult(
                oidcAuthorization = OIDC_AUTHZ,
                dpopAuthorization = DPOP_AUTHZ,
                nonce = RANDOM_NONCE
            )
            const val NONCE_HEADER_KEY = "Replay-Nonce"
            const val LOCATION_HEADER_KEY = "location"
            val HEADERS = mapOf(NONCE_HEADER_KEY to RANDOM_NONCE.value, LOCATION_HEADER_KEY to RANDOM_URL)

            val E2EI_TEAM_SETTINGS = E2EISettings(
                true, RANDOM_URL, DateTimeUtil.currentInstant()
            )
        }
    }
}
