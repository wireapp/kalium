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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.cryptography.AcmeChallenge
import com.wire.kalium.cryptography.AcmeDirectory
import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.cryptography.NewAcmeOrder
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.e2ei.AuthorizationResult
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.Nonce
import com.wire.kalium.logic.feature.e2ei.usecase.E2EIEnrollmentResult
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ExperimentalCoroutinesApi
class EnrollE2EICertificateUseCaseTest {

    @Test
    fun givenLoadACMEDirectoriesFails_whenInvokeUseCase_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withFetchFederationCertificateChainResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(E2EIFailure.AcmeDirectories(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.loadACMEDirectories()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getACMENonce(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getOAuthRefreshToken()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenGetACMENonceFails_whenInvokeUseCase_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withFetchFederationCertificateChainResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(E2EIFailure.AcmeNonce(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.loadACMEDirectories()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.getACMENonce(any<String>())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getOAuthRefreshToken()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenCreateNewAccountFails_whenInvokeUseCase_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withFetchFederationCertificateChainResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewAccountResulting(E2EIFailure.AcmeNewAccount(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.loadACMEDirectories()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getACMENonce(any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewAccount(any<Nonce>(), any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getOAuthRefreshToken()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCreateNewOrderFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withFetchFederationCertificateChainResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewOrderResulting(E2EIFailure.AcmeNewOrder(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.loadACMEDirectories()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getACMENonce(any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewAccount(any<Nonce>(), any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewOrder(any<Nonce>(), any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getOAuthRefreshToken()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCreateAuthorizationsFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withFetchFederationCertificateChainResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
        arrangement.withGettingChallenges(E2EIFailure.AcmeAuthorizations.left())

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.loadACMEDirectories()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getACMENonce(any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewAccount(any<Nonce>(), any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewOrder(any<Nonce>(), any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getAuthorizations(any<Nonce>(), any<List<String>>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getOAuthRefreshToken()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCallingInitialization_thenReturnInitializationResult() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        val expected = INITIALIZATION_RESULT

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withFetchFederationCertificateChainResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
        arrangement.withGettingRefreshTokenSucceeding()
        arrangement.withGettingChallenges(Either.Right(AUTHORIZATIONS))

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then

        result.shouldSucceed()

        assertIs<E2EIEnrollmentResult.Initialized>(result.value)

        assertEquals(expected, result.value as E2EIEnrollmentResult.Initialized)

        coVerify {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.loadACMEDirectories()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getACMENonce(any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewAccount(any<Nonce>(), any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.createNewOrder(any<Nonce>(), any<String>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getAuthorizations(any<Nonce>(), any<List<String>>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getOAuthRefreshToken()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetWireNonceFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(E2EIFailure.WireNonce(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetDPoPTokenFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(E2EIFailure.DPoPToken(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetWireAccessTokenFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(E2EIFailure.DPoPChallenge(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenValidateDPoPChallengeFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(E2EIFailure.DPoPChallenge(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenValidateOIDCChallengeFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withValidateOIDCChallengeResulting(E2EIFailure.OIDCChallenge(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCheckOrderRequestFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withCheckOrderRequestResulting(E2EIFailure.CheckOrderRequest(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.finalize(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenFinalizeFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
        arrangement.withFinalizeResulting(E2EIFailure.FinalizeRequest(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.finalize(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCertificateRequestFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
        arrangement.withFinalizeResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
        arrangement.withRotateKeysAndMigrateConversations(Either.Right(Unit))
        arrangement.withCertificateRequestResulting(E2EIFailure.Certificate(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.finalize(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.certificateRequest(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenRotatingKeysAndMigratingConversationsFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
        arrangement.withFinalizeResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
        arrangement.withCertificateRequestResulting(Either.Right(ACME_RESPONSE))
        arrangement.withRotateKeysAndMigrateConversations(E2EIFailure.RotationAndMigration(TEST_CORE_FAILURE).left())

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.e2EIRepository.finalize(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any<String>(), any<Boolean>())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUseCase_whenEveryStepSucceed_thenShouldSucceed() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
        arrangement.withFinalizeResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
        arrangement.withCertificateRequestResulting(Either.Right(ACME_RESPONSE))
        arrangement.withRotateKeysAndMigrateConversations(Either.Right(Unit))

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldSucceed()

        coVerify {
            arrangement.e2EIRepository.getWireNonce()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getDPoPToken(any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.checkOrderRequest(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.finalize(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.certificateRequest(any<String>(), any<Nonce>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any<String>(), any<Boolean>())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val e2EIRepository = mock(E2EIRepository::class)

        suspend fun withInitializingE2EIClientSucceed() = apply {
            coEvery {
                e2EIRepository.initFreshE2EIClient(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withLoadTrustAnchorsResulting(result: Either<E2EIFailure, Unit>) = apply {
            coEvery {
                e2EIRepository.fetchAndSetTrustAnchors()
            }.returns(result)
        }

        suspend fun withFetchFederationCertificateChainResulting(result: Either<E2EIFailure, Unit>) = apply {
            coEvery {
                e2EIRepository.fetchFederationCertificates()
            }.returns(result)
        }

        suspend fun withLoadACMEDirectoriesResulting(result: Either<E2EIFailure, AcmeDirectory>) = apply {
            coEvery {
                e2EIRepository.loadACMEDirectories()
            }.returns(result)
        }

        suspend fun withGetACMENonceResulting(result: Either<E2EIFailure, Nonce>) = apply {
            coEvery {
                e2EIRepository.getACMENonce(any())
            }.returns(result)
        }

        suspend fun withCreateNewAccountResulting(result: Either<E2EIFailure, Nonce>) = apply {
            coEvery {
                e2EIRepository.createNewAccount(any(), any())
            }.returns(result)
        }

        suspend fun withCreateNewOrderResulting(result: Either<E2EIFailure, Triple<NewAcmeOrder, Nonce, String>>) = apply {
            coEvery {
                e2EIRepository.createNewOrder(any(), any())
            }.returns(result)
        }

        suspend fun withGettingChallenges(result: Either<E2EIFailure, AuthorizationResult>) = apply {
            coEvery {
                e2EIRepository.getAuthorizations(any(), any())
            }.returns(result)
        }

        suspend fun withGettingRefreshTokenSucceeding() = apply {
            coEvery {
                e2EIRepository.getOAuthRefreshToken()
            }.returns(Either.Right(REFRESH_TOKEN))
        }

        suspend fun withGetWireNonceResulting(result: Either<E2EIFailure, Nonce>) = apply {
            coEvery {
                e2EIRepository.getWireNonce()
            }.returns(result)
        }

        suspend fun withGetDPoPTokenResulting(result: Either<E2EIFailure, String>) = apply {
            coEvery {
                e2EIRepository.getDPoPToken(any())
            }.returns(result)
        }

        suspend fun withGetWireAccessTokenResulting(result: Either<E2EIFailure, AccessTokenResponse>) = apply {
            coEvery {
                e2EIRepository.getWireAccessToken(any())
            }.returns(result)
        }

        suspend fun withValidateDPoPChallengeResulting(result: Either<E2EIFailure, ChallengeResponse>) = apply {
            coEvery {
                e2EIRepository.validateDPoPChallenge(any(), any(), any())
            }.returns(result)
        }

        suspend fun withValidateOIDCChallengeResulting(result: Either<E2EIFailure, ChallengeResponse>) = apply {
            coEvery {
                e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
            }.returns(result)
        }

        suspend fun withCheckOrderRequestResulting(result: Either<E2EIFailure, Pair<ACMEResponse, String>>) = apply {
            coEvery {
                e2EIRepository.checkOrderRequest(any(), any())
            }.returns(result)
        }

        suspend fun withFinalizeResulting(result: Either<E2EIFailure, Pair<ACMEResponse, String>>) = apply {
            coEvery {
                e2EIRepository.finalize(any(), any())
            }.returns(result)
        }

        suspend fun withRotateKeysAndMigrateConversations(result: Either<E2EIFailure, Unit>) = apply {
            coEvery {
                e2EIRepository.rotateKeysAndMigrateConversations(any(), any())
            }.returns(result)
        }

        suspend fun withCertificateRequestResulting(result: Either<E2EIFailure, ACMEResponse>) = apply {
            coEvery {
                e2EIRepository.certificateRequest(any(), any())
            }.returns(result)
        }

        fun arrange(): Pair<Arrangement, EnrollE2EIUseCase> = this to EnrollE2EIUseCaseImpl(e2EIRepository)
    }

    companion object {
        val RANDOM_ID_TOKEN = "idToken"
        val RANDOM_DPoP_TOKEN = "dpopToken"
        val RANDOM_NONCE = Nonce("random-nonce")
        val REFRESH_TOKEN = "YRjxLpsjRqL7zYuKstXogqioA_P3Z4fiEuga0NCVRcDSc8cy_9msxg"
        val TEST_CORE_FAILURE = CoreFailure.Unknown(Throwable("an error"))
        val ACME_BASE_URL = "https://balderdash.hogwash.work:9000"
        val RANDOM_LOCATION = "https://balderdash.hogwash.work:9000"
        val RANDOM_BYTE_ARRAY = "random-value".encodeToByteArray()
        val ACME_DIRECTORIES = AcmeDirectory(
            newNonce = "${ACME_BASE_URL}/acme/wire/new-nonce",
            newAccount = "${ACME_BASE_URL}/acme/wire/new-account",
            newOrder = "${ACME_BASE_URL}/acme/wire/new-order"
        )
        val ACME_ORDER = NewAcmeOrder(
            delegate = RANDOM_BYTE_ARRAY,
            authorizations = listOf(RANDOM_LOCATION, RANDOM_LOCATION)
        )

        val ACME_CHALLENGE = AcmeChallenge(
            delegate = RANDOM_BYTE_ARRAY,
            url = RANDOM_LOCATION,
            target = RANDOM_LOCATION
        )

        val OIDC_AUTHZ = NewAcmeAuthz(
            identifier = "identifier",
            keyAuth = "keyauth",
            challenge = ACME_CHALLENGE
        )

        val DPOP_AUTHZ = NewAcmeAuthz(
            identifier = "identifier", keyAuth = null, challenge = ACME_CHALLENGE
        )
        val OAUTH_CLAIMS = JsonObject(
            mapOf(
                "id_token" to JsonObject(
                    mapOf(
                        "keyauth" to JsonObject(
                            mapOf("essential" to JsonPrimitive(true), "value" to JsonPrimitive(OIDC_AUTHZ.keyAuth))
                        ), "acme_aud" to JsonObject(
                            mapOf("essential" to JsonPrimitive(true), "value" to JsonPrimitive(OIDC_AUTHZ.challenge.url))
                        )
                    )
                )
            )
        )

        val AUTHORIZATIONS = AuthorizationResult(
            oidcAuthorization = OIDC_AUTHZ, dpopAuthorization = DPOP_AUTHZ,
            nonce = RANDOM_NONCE
        )

        val WIRE_ACCESS_TOKEN = AccessTokenResponse(
            expiresIn = "2021-05-12T10:52:02.671Z",
            token = "random-token",
            type = "random-type"
        )

        val ACME_CHALLENGE_RESPONSE = ChallengeResponse(
            type = "type",
            url = "url",
            status = "status",
            token = "token",
            target = "target",
            nonce = "nonce"
        )

        val ACME_RESPONSE = ACMEResponse(
            nonce = RANDOM_NONCE.value,
            location = RANDOM_LOCATION,
            response = RANDOM_BYTE_ARRAY
        )

        val INITIALIZATION_RESULT = E2EIEnrollmentResult.Initialized(
            target = ACME_CHALLENGE.target,
            oAuthState = REFRESH_TOKEN, dPopAuthorizations = DPOP_AUTHZ, oidcAuthorizations = OIDC_AUTHZ,
            oAuthClaims = OAUTH_CLAIMS,
            lastNonce = RANDOM_NONCE,
            orderLocation = RANDOM_LOCATION
        )
    }
}
