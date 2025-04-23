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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.AcmeChallenge
import com.wire.kalium.cryptography.AcmeDirectory
import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.cryptography.NewAcmeOrder
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.e2ei.AuthorizationResult
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.Nonce
import com.wire.kalium.logic.feature.e2ei.usecase.E2EIEnrollmentResult
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.framework.TestConversation.MLS_CONVERSATION
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.unbound.acme.ChallengeResponse
import io.mockative.AnySuspendResultBuilder
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ExperimentalCoroutinesApi
class EnrollE2EICertificateUseCaseTest {

    private lateinit var coroutineScope: TestScope

    @BeforeTest
    fun setup() {
        coroutineScope = TestScope()
    }

    @AfterTest
    fun tearDown() {
        coroutineScope.cancel()
    }

    @Test
    fun givenLoadACMEDirectoriesFails_whenInvokeUseCase_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(E2EIFailure.AcmeDirectories(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenGetACMENonceFails_whenInvokeUseCase_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(E2EIFailure.AcmeNonce(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenCreateNewAccountFails_whenInvokeUseCase_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(E2EIFailure.AcmeNewAccount(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCreateNewOrderFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
            withCreateNewOrderResulting(E2EIFailure.AcmeNewOrder(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCreateAuthorizationsFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
            withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
            withGettingChallenges(E2EIFailure.AcmeAuthorizations.left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCallingInitialization_thenReturnInitializationResult() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
            withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
            withGettingRefreshTokenSucceeding()
            withGettingChallenges(Either.Right(AUTHORIZATIONS))
            withSelfUserFetched(true)
        }

        val expected = INITIALIZATION_RESULT

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetWireNonceFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(E2EIFailure.WireNonce(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetDPoPTokenFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(E2EIFailure.DPoPToken(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetWireAccessTokenFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(E2EIFailure.DPoPChallenge(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenValidateDPoPChallengeFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(E2EIFailure.DPoPChallenge(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenValidateOIDCChallengeFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(E2EIFailure.OIDCChallenge(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCheckOrderRequestFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withCheckOrderRequestResulting(E2EIFailure.CheckOrderRequest(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenFinalizeFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withFinalizeResulting(E2EIFailure.FinalizeRequest(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCertificateRequestFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withFinalizeResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withRotateKeysAndMigrateConversations(Either.Right(Unit))
            withCertificateRequestResulting(E2EIFailure.Certificate(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenRotatingKeysAndMigratingConversationsFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withFinalizeResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withCertificateRequestResulting(Either.Right(ACME_RESPONSE))
            withRotateKeysAndMigrateConversations(E2EIFailure.RotationAndMigration(TEST_CORE_FAILURE).left())
            withObserveConversationListResulting(listOf(MLS_CONVERSATION))
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any<String>(), any(), any<Boolean>())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUseCase_whenEveryStepSucceed_thenShouldSucceed() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withFinalizeResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withCertificateRequestResulting(Either.Right(ACME_RESPONSE))
            withRotateKeysAndMigrateConversations(Either.Right(Unit))
            withObserveConversationListResulting(listOf(MLS_CONVERSATION))
            withSelfUserFetched(true)
        }

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
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any<String>(), any(), any<Boolean>())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCertEnrollForNewClient_whenEnrolling_thenUpdateSelfUserInfo() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            // given
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
            withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
            withGettingRefreshTokenSucceeding()
            withGettingChallenges(Either.Right(AUTHORIZATIONS))
            withSelfUserFetched(false)
            withFetchSelfUser(Unit.right())
        }

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment(true)

        // then
        result.shouldSucceed()
        advanceUntilIdle()
        coVerify {
            arrangement.userRepository.fetchSelfUser()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement : UserRepositoryArrangement by UserRepositoryArrangementImpl() {

        val e2EIRepository = mock(E2EIRepository::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        private var selfUserFetched: Boolean = false

        // to ensure that at the moment of the given call, the user is already fetched
        private fun <R> AnySuspendResultBuilder<R>.ensuresSelfUserFetchedAndReturns(value: R, methodName: String) = this.invokes {
            require(selfUserFetched) { "Self user should be fetched before calling $methodName" }
            value
        }

        fun withSelfUserFetched(isFetched: Boolean = true) {
            selfUserFetched = isFetched
        }

        override suspend fun withFetchSelfUser(result: Either<CoreFailure, Unit>) {
            coEvery {
                userRepository.fetchSelfUser()
            }.invokes { _ ->
                selfUserFetched = true
                result
            }
        }

        suspend fun withInitializingE2EIClientSucceed() = apply {
            coEvery {
                e2EIRepository.initFreshE2EIClient(any(), any())
            }.ensuresSelfUserFetchedAndReturns(Either.Right(Unit), e2EIRepository::initFreshE2EIClient.name)
        }

        suspend fun withLoadTrustAnchorsResulting(result: Either<E2EIFailure, Unit>) = apply {
            coEvery {
                e2EIRepository.fetchAndSetTrustAnchors()
            }.returns(result)
        }

        suspend fun withFetchFederationCertificateChainResulting(result: Either<E2EIFailure, Unit>) = apply {
            coEvery {
                e2EIRepository.fetchFederationCertificates()
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::fetchFederationCertificates.name)
        }

        suspend fun withLoadACMEDirectoriesResulting(result: Either<E2EIFailure, AcmeDirectory>) = apply {
            coEvery {
                e2EIRepository.loadACMEDirectories()
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::loadACMEDirectories.name)
        }

        suspend fun withGetACMENonceResulting(result: Either<E2EIFailure, Nonce>) = apply {
            coEvery {
                e2EIRepository.getACMENonce(any())
            }.returns(result)
        }

        suspend fun withCreateNewAccountResulting(result: Either<E2EIFailure, Nonce>) = apply {
            coEvery {
                e2EIRepository.createNewAccount(any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::createNewAccount.name)
        }

        suspend fun withCreateNewOrderResulting(result: Either<E2EIFailure, Triple<NewAcmeOrder, Nonce, String>>) = apply {
            coEvery {
                e2EIRepository.createNewOrder(any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::createNewOrder.name)
        }

        suspend fun withGettingChallenges(result: Either<E2EIFailure, AuthorizationResult>) = apply {
            coEvery {
                e2EIRepository.getAuthorizations(any(), any())
            }.returns(result)
        }

        suspend fun withGettingRefreshTokenSucceeding() = apply {
            coEvery {
                e2EIRepository.getOAuthRefreshToken()
            }.ensuresSelfUserFetchedAndReturns(Either.Right(REFRESH_TOKEN), e2EIRepository::getOAuthRefreshToken.name)
        }

        suspend fun withGetWireNonceResulting(result: Either<E2EIFailure, Nonce>) = apply {
            coEvery {
                e2EIRepository.getWireNonce()
            }.returns(result)
        }

        suspend fun withGetDPoPTokenResulting(result: Either<E2EIFailure, String>) = apply {
            coEvery {
                e2EIRepository.getDPoPToken(any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::getDPoPToken.name)
        }

        suspend fun withGetWireAccessTokenResulting(result: Either<E2EIFailure, AccessTokenResponse>) = apply {
            coEvery {
                e2EIRepository.getWireAccessToken(any())
            }.returns(result)
        }

        suspend fun withValidateDPoPChallengeResulting(result: Either<E2EIFailure, ChallengeResponse>) = apply {
            coEvery {
                e2EIRepository.validateDPoPChallenge(any(), any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::validateDPoPChallenge.name)
        }

        suspend fun withValidateOIDCChallengeResulting(result: Either<E2EIFailure, ChallengeResponse>) = apply {
            coEvery {
                e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::validateOIDCChallenge.name)
        }

        suspend fun withCheckOrderRequestResulting(result: Either<E2EIFailure, Pair<ACMEResponse, String>>) = apply {
            coEvery {
                e2EIRepository.checkOrderRequest(any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::checkOrderRequest.name)
        }

        suspend fun withFinalizeResulting(result: Either<E2EIFailure, Pair<ACMEResponse, String>>) = apply {
            coEvery {
                e2EIRepository.finalize(any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::finalize.name)
        }

        suspend fun withRotateKeysAndMigrateConversations(result: Either<E2EIFailure, Unit>) = apply {
            coEvery {
                e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::rotateKeysAndMigrateConversations.name)
        }

        suspend fun withCertificateRequestResulting(result: Either<E2EIFailure, ACMEResponse>) = apply {
            coEvery {
                e2EIRepository.certificateRequest(any(), any())
            }.ensuresSelfUserFetchedAndReturns(result, e2EIRepository::certificateRequest.name)
        }

        suspend fun withObserveConversationListResulting(result: List<Conversation>) = apply {
            coEvery {
                conversationRepository.observeConversationList()
            }.returns(flowOf(result))
        }

        suspend fun arrange(coroutineScope: CoroutineScope, block: suspend Arrangement.() -> Unit): Pair<Arrangement, EnrollE2EIUseCase> =
            apply {
                block()
            }.let {
                this to EnrollE2EIUseCaseImpl(
                    e2EIRepository = e2EIRepository,
                    userRepository = userRepository,
                    coroutineScope = coroutineScope,
                    conversationRepository = conversationRepository
                )
            }
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
