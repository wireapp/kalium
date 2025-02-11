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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.unbound.acme.ACMEAuthorizationResponse
import com.wire.kalium.network.api.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.api.unbound.acme.ChallengeResponse
import com.wire.kalium.network.api.unbound.acme.DtoAuthorizationChallengeType
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
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

        coVerify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.getACMEDirectories(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.e2eiClient.directoryResponse(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.getACMEDirectories(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.directoryResponse(any<ByteArray>())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.getACMEDirectories(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.directoryResponse(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.getNewAccountRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setAccountResponse(any<ByteArray>())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClient.getNewAccountRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setAccountResponse(any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.getNewOrderRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setOrderResponse(any<ByteArray>())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClient.getNewOrderRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setOrderResponse(any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.getNewAuthzRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendAuthorizationRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setAuthzResponse(any<ByteArray>())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClient.getNewAuthzRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendAuthorizationRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setAuthzResponse(any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.getNewAuthzRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendAuthorizationRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setAuthzResponse(any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.getNewAuthzRequest(any<String>(), any())
        }.wasInvoked(authorizationsUrls.size.time)

        coVerify {
            arrangement.acmeApi.sendAuthorizationRequest(any<String>(), any())
        }.wasInvoked(authorizationsUrls.size.time)

        coVerify {
            arrangement.e2eiClient.setAuthzResponse(any<ByteArray>())
        }.wasInvoked(authorizationsUrls.size.time)
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

        coVerify {
            arrangement.e2eiClient.getNewDpopChallengeRequest(any<String>(), any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendChallengeRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setDPoPChallengeResponse(any<ByteArray>())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClient.getNewDpopChallengeRequest(any<String>(), any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendChallengeRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setOIDCChallengeResponse(any<CoreCryptoCentral>(), any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.getNewOidcChallengeRequest(any<String>(), any<String>(), any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendChallengeRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setOIDCChallengeResponse(any<CoreCryptoCentral>(), any<ByteArray>())
        }.wasInvoked(once)
    }

    @Test
    fun givenOIDCChallengeRequestSucceedWithInvalidStatus_whenCallingValidateDPoPChallenge_thenItFail() = runTest {
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withGetCoreCryptoSuccessful()
            .withSendChallengeRequestApiSucceedWithInvalidStatus()
            .withGetE2EIClientSuccessful()
            .withGetMLSClientSuccessful()
            .withGetNewOidcChallengeRequest()
            .arrange()

        // When
        val result = e2eiRepository.validateOIDCChallenge(RANDOM_ID_TOKEN, REFRESH_TOKEN, RANDOM_NONCE, ACME_CHALLENGE)

        // Then
        result.shouldFail()

        coVerify {
            arrangement.e2eiClient.getNewOidcChallengeRequest(any<String>(), any<String>(), any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendChallengeRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setOIDCChallengeResponse(any<CoreCryptoCentral>(), any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.getNewOidcChallengeRequest(any<String>(), any<String>(), any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendChallengeRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.setOIDCChallengeResponse(any<CoreCryptoCentral>(), any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.checkOrderRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.checkOrderResponse(any<ByteArray>())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClient.checkOrderRequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.checkOrderResponse(any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.finalizeRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.finalizeResponse(any<ByteArray>())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClient.finalizeRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.e2eiClient.finalizeResponse(any<ByteArray>())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.e2eiClient.certificateRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClient.certificateRequest(any<String>())
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.sendACMERequest(any<String>(), any())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClientProvider.getE2EIClient(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.e2eiClientProvider.getE2EIClient(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }.wasInvoked(once)
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

        coVerify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.getACMEFederationCertificateChain(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.coreCryptoCentral.registerIntermediateCa(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenACMEFederationApiSucceeds_whenFetchACMECertificates_thenAllCertificatesAreRegistered() = runTest {
        val certificateList = listOf("a", "b", "potato")
        // Given
        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS))
            .withAcmeFederationApiSucceed(certificateList)
            .withCurrentClientIdProviderSuccessful()
            .withGetCoreCryptoSuccessful()
            .withRegisterIntermediateCABag()
            .arrange()

        // When
        val result = e2eiRepository.fetchFederationCertificates()

        // Then
        result.shouldSucceed()

        coVerify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.getACMEFederationCertificateChain(any())
        }.wasInvoked(once)

        certificateList.forEach { certificateValue ->
            coVerify {
                arrangement.coreCryptoCentral.registerIntermediateCa(eq(certificateValue))
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenGettingE2EITeamSettingsFails_whenFetchATrustAnchors_thenItFail() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withSetShouldFetchE2EIGetTrustAnchors()
            .withGetShouldFetchE2EITrustAnchors(true)
            .withGettingE2EISettingsReturns(Either.Left(StorageFailure.DataNotFound))
            .withFetchAcmeTrustAnchorsApiFails()
            .withGetMLSClientSuccessful()
            .arrange()

        // When
        val result = e2eiRepository.fetchAndSetTrustAnchors()

        // Then
        result.shouldFail()

        coVerify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.getACMEFederationCertificateChain(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.coreCryptoCentral.registerIntermediateCa(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenACMETrustAnchorsApiSucceed_whenFetchACMETrustAnchors_thenItSucceed() = runTest {
        // Given

        val (arrangement, e2eiRepository) = Arrangement()
            .withSetShouldFetchE2EIGetTrustAnchors()
            .withGetShouldFetchE2EITrustAnchors(true)
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

        coVerify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)

        coVerify {
            arrangement.acmeApi.getTrustAnchors(eq(RANDOM_URL))
        }.wasInvoked(once)

        coVerify {
            arrangement.coreCryptoCentral.registerTrustAnchors(eq(Arrangement.RANDOM_BYTE_ARRAY.decodeToString()))
        }.wasInvoked(once)

        coVerify {
            arrangement.userConfigRepository.setShouldFetchE2EITrustAnchors(eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun givenGetTrustAnchorsHasAlreadyFetchedOnce_whenFetchingTrustAnchors_thenReturnE2EIFailureTrustAnchorsAlreadyFetched() = runTest {
        // given
        val (arrangement, e2eiRepository) = Arrangement()
            .withSetShouldFetchE2EIGetTrustAnchors()
            .withGetShouldFetchE2EITrustAnchors(false)
            .arrange()

        // when
        val result = e2eiRepository.fetchAndSetTrustAnchors()

        // then
        result.shouldSucceed()

        coVerify {
            arrangement.userConfigRepository.getShouldFetchE2EITrustAnchor()
        }.wasInvoked(once)
    }

    @Test
    fun givenE2EIIsDisabled_whenCallingDiscoveryUrl_thenItFailWithDisabled() {
        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EISettings(false, null, Instant.DISTANT_FUTURE, false, null)))
            .arrange()

        e2eiRepository.discoveryUrl().shouldFail {
            assertIs<E2EIFailure.Disabled>(it)
        }

        verify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)
    }

    @Test
    fun givenE2EIIsEnabledAndDiscoveryUrlIsNull_whenCallingDiscoveryUrl_thenItFailWithMissingDiscoveryUrl() {
        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EISettings(true, null, Instant.DISTANT_FUTURE, false, null)))
            .arrange()

        e2eiRepository.discoveryUrl().shouldFail {
            assertIs<E2EIFailure.MissingDiscoveryUrl>(it)
        }

        verify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)
    }

    @Test
    fun givenE2EIIsEnabledAndDiscoveryUrlIsNotNull_whenCallingDiscoveryUrl_thenItSucceed() {
        val (arrangement, e2eiRepository) = Arrangement()
            .withGettingE2EISettingsReturns(Either.Right(E2EISettings(true, RANDOM_URL, Instant.DISTANT_FUTURE, false, null)))
            .arrange()

        e2eiRepository.discoveryUrl().shouldSucceed {
            assertEquals(RANDOM_URL, it)
        }

        verify {
            arrangement.userConfigRepository.getE2EISettings()
        }.wasInvoked(once)
    }

    private class Arrangement {

        suspend fun withGetE2EIClientSuccessful() = apply {
            coEvery {
                e2eiClientProvider.getE2EIClient(any(), any())
            }.returns(Either.Right(e2eiClient))
        }

        suspend fun withGetCoreCryptoSuccessful() = apply {
            coEvery {
                mlsClientProvider.getCoreCrypto(any())
            }.returns(Either.Right(coreCryptoCentral))
        }

        suspend fun withE2EIClientLoadDirectoriesSuccessful() = apply {
            coEvery {
                e2eiClient.directoryResponse(any())
            }.returns(ACME_DIRECTORIES)
        }

        suspend fun withGetNewAccountSuccessful() = apply {
            coEvery {
                e2eiClient.getNewAccountRequest(any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withGetNewOrderSuccessful() = apply {
            coEvery {
                e2eiClient.getNewOrderRequest(any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withGetNewAuthzRequestSuccessful() = apply {
            coEvery {
                e2eiClient.getNewAuthzRequest(any(), any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withCheckOrderRequestSuccessful() = apply {
            coEvery {
                e2eiClient.checkOrderRequest(any(), any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withFinalizeRequestSuccessful() = apply {
            coEvery {
                e2eiClient.finalizeRequest(any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withCertificateRequestSuccessful() = apply {
            coEvery {
                e2eiClient.certificateRequest(any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withRotateKeysAndMigrateConversationsReturns(result: Either<E2EIFailure, Unit>) = apply {
            coEvery {
                mlsConversationRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
            }.returns(result)
        }

        suspend fun withCurrentClientIdProviderSuccessful() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        suspend fun withFinalizeResponseSuccessful() = apply {
            coEvery {
                e2eiClient.finalizeResponse(any())
            }.returns("")
        }

        suspend fun withCheckOrderResponseSuccessful() = apply {
            coEvery {
                e2eiClient.checkOrderResponse(any())
            }.returns("")
        }

        suspend fun withGetNewDpopChallengeRequest() = apply {
            coEvery {
                e2eiClient.getNewDpopChallengeRequest(any(), any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withGetNewOidcChallengeRequest() = apply {
            coEvery {
                e2eiClient.getNewOidcChallengeRequest(any(), any(), any())
            }.returns(RANDOM_BYTE_ARRAY)
        }

        suspend fun withSetOrderResponseSuccessful() = apply {
            coEvery {
                e2eiClient.setOrderResponse(any())
            }.returns(ACME_ORDER)
        }

        suspend fun withSetAuthzResponseSuccessful() = apply {
            coEvery {
                e2eiClient.setAuthzResponse(any())
            }.returns(OIDC_AUTHZ)
        }

        suspend fun withGetMLSClientSuccessful() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))
        }

        fun withGettingE2EISettingsReturns(result: Either<StorageFailure, E2EISettings>) = apply {
            every {
                userConfigRepository.getE2EISettings()
            }.returns(result)
        }

        suspend fun withGetShouldFetchE2EITrustAnchors(result: Boolean) = apply {
            coEvery {
                userConfigRepository.getShouldFetchE2EITrustAnchor()
            }.returns(result)
        }

        suspend fun withSetShouldFetchE2EIGetTrustAnchors() = apply {
            coEvery {
                userConfigRepository.setShouldFetchE2EITrustAnchors(any())
            }.returns(Unit)
        }


        suspend fun withAcmeDirectoriesApiSucceed() = apply {
            coEvery {
                acmeApi.getACMEDirectories(any())
            }.returns(NetworkResponse.Success(ACME_DIRECTORIES_RESPONSE, mapOf(), 200))
        }

        suspend fun withAcmeDirectoriesApiFails() = apply {
            coEvery {
                acmeApi.getACMEDirectories(any())
            }.returns(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        suspend fun withSendAcmeRequestApiSucceed() = apply {
            coEvery {
                acmeApi.sendACMERequest(any(), any())
            }.returns(NetworkResponse.Success(ACME_REQUEST_RESPONSE, mapOf(), 200))
        }

        suspend fun withSendAcmeRequestApiFails() = apply {
            coEvery {
                acmeApi.sendACMERequest(any(), any())
            }.returns(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        suspend fun withSendAuthorizationRequestSucceed(url: String, challengeType: DtoAuthorizationChallengeType) = apply {
            coEvery {
                acmeApi.sendAuthorizationRequest(eq(url), any())
            }.returns(
                    NetworkResponse.Success(
                        ACME_AUTHORIZATION_RESPONSE.copy(challengeType = challengeType),
                        headers = HEADERS,
                        200
                    )
                )
        }

        suspend fun withSendAuthorizationRequestFails() = apply {
            coEvery {
                acmeApi.sendAuthorizationRequest(any(), any())
            }.returns(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        suspend fun withSendChallengeRequestApiSucceed() = apply {
            coEvery {
                acmeApi.sendChallengeRequest(any(), any())
            }.returns(NetworkResponse.Success(ACME_CHALLENGE_RESPONSE, mapOf(), 200))
        }

        suspend fun withSendChallengeRequestApiSucceedWithInvalidStatus() = apply {
            coEvery {
                acmeApi.sendChallengeRequest(any(), any())
            }.returns(NetworkResponse.Success(ACME_CHALLENGE_RESPONSE.copy(status = "invalid"), mapOf(), 200))
        }

        suspend fun withSendChallengeRequestApiFails() = apply {
            coEvery {
                acmeApi.sendChallengeRequest(any(), any())
            }.returns(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        suspend fun withAcmeFederationApiSucceed(certificateList: List<String>) = apply {
            coEvery {
                acmeApi.getACMEFederationCertificateChain(any())
            }.returns(NetworkResponse.Success(certificateList, mapOf(), 200))
        }

        suspend fun withAcmeFederationApiFails() = apply {
            coEvery {
                acmeApi.getACMEFederationCertificateChain(any())
            }.returns(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        suspend fun withFetchAcmeTrustAnchorsApiFails() = apply {
            coEvery {
                acmeApi.getTrustAnchors(any())
            }.returns(NetworkResponse.Error(INVALID_REQUEST_ERROR))
        }

        suspend fun withFetchAcmeTrustAnchorsApiSucceed() = apply {
            coEvery {
                acmeApi.getTrustAnchors(any())
            }.returns(NetworkResponse.Success(RANDOM_BYTE_ARRAY, mapOf(), 200))
        }

        suspend fun withRegisterIntermediateCABag() = apply {
            coEvery {
                coreCryptoCentral.registerIntermediateCa(any())
            }.returns(Unit)
        }

        suspend fun withRegisterTrustAnchors() = apply {
            coEvery {
                coreCryptoCentral.registerTrustAnchors(any())
            }.returns(Unit)
        }

        @Mock
        val e2eiApi: E2EIApi = mock(E2EIApi::class)

        @Mock
        val acmeApi: ACMEApi = mock(ACMEApi::class)

        @Mock
        val e2eiClientProvider: E2EIClientProvider = mock(E2EIClientProvider::class)

        @Mock
        val e2eiClient = mock(E2EIClient::class)

        @Mock
        val coreCryptoCentral = mock(CoreCryptoCentral::class)

        @Mock
        val mlsClientProvider: MLSClientProvider = mock(MLSClientProvider::class)

        @Mock
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val mlsClient = mock(MLSClient::class)

        @Mock
        val currentClientIdProvider: CurrentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

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
                true, RANDOM_URL, DateTimeUtil.currentInstant(), false, null
            )
        }
    }
}
