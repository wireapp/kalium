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
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.e2ei.usecase.E2EIEnrollmentResult
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.FinalizeEnrollmentResult
import com.wire.kalium.logic.feature.e2ei.usecase.InitialEnrollmentResult
import com.wire.kalium.logic.framework.TestConversation.MLS_CONVERSATION
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.unbound.acme.ChallengeResponse
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
internal class EnrollE2EICertificateUseCaseTest {

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
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(E2EIFailure.AcmeDirectories(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.initialEnrollment()

        assertIs<InitialEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.loadACMEDirectories()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getACMENonce(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenGetACMENonceFails_whenInvokeUseCase_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(E2EIFailure.AcmeNonce(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.initialEnrollment()

        assertIs<InitialEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.loadACMEDirectories()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getACMENonce(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenCreateNewAccountFails_whenInvokeUseCase_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(E2EIFailure.AcmeNewAccount(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.initialEnrollment()

        assertIs<InitialEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.loadACMEDirectories()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getACMENonce(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenCreateNewOrderFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
            withCreateNewOrderResulting(E2EIFailure.AcmeNewOrder(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.initialEnrollment()

        assertIs<InitialEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.loadACMEDirectories()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getACMENonce(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenCreateAuthorizationsFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
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

        val result = enrollE2EICertificateUseCase.initialEnrollment()

        assertIs<InitialEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.loadACMEDirectories()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getACMENonce(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenCallingInitialization_thenReturnInitializationResult() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
            withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
            withGettingChallenges(Either.Right(AUTHORIZATIONS))
            withSelfUserFetched(true)
        }

        val expected = INITIALIZATION_RESULT

        val result = enrollE2EICertificateUseCase.initialEnrollment()

        assertIs<InitialEnrollmentResult.Success>(result)

        assertEquals(expected, result.initializationResult)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.fetchAndSetTrustAnchors()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.loadACMEDirectories()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getACMENonce(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.createNewAccount(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.createNewOrder(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getAuthorizations(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenGetWireNonceFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withGetWireNonceResulting(E2EIFailure.WireNonce(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenGetDPoPTokenFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(E2EIFailure.DPoPToken(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.getWireAccessToken(any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenGetWireAccessTokenFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(E2EIFailure.DPoPChallenge(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateDPoPChallenge(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenValidateDPoPChallengeFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(E2EIFailure.DPoPChallenge(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any(), eq(DPOP_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenValidateOIDCChallengeFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(E2EIFailure.OIDCChallenge(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any(), eq(DPOP_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any(), eq(OIDC_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenCheckOrderRequestFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withCheckOrderRequestResulting(E2EIFailure.CheckOrderRequest(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any(), eq(DPOP_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any(), eq(OIDC_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenFinalizeFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
            withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
            withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
            withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withValidateOIDCChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
            withCheckOrderRequestResulting(Either.Right((ACME_RESPONSE to RANDOM_LOCATION)))
            withFinalizeResulting(E2EIFailure.FinalizeRequest(TEST_CORE_FAILURE).left())
            withSelfUserFetched(true)
        }

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any(), eq(DPOP_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any(), eq(OIDC_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.finalize(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenCertificateRequestFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
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

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any(), eq(DPOP_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any(), eq(OIDC_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.finalize(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenRotatingKeysAndMigratingConversationsFailing_thenReturnFailure() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
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

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any(), eq(DPOP_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any(), eq(OIDC_AUTHZ.challenge))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.finalize(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenUseCase_whenEveryStepSucceed_thenShouldSucceed() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
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

        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        assertIs<FinalizeEnrollmentResult.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireNonce()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getDPoPToken(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.getWireAccessToken(eq(RANDOM_DPoP_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateDPoPChallenge(eq(WIRE_ACCESS_TOKEN.token), any(), eq(DPOP_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.validateOIDCChallenge(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any(), eq(OIDC_AUTHZ.challenge))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.checkOrderRequest(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.finalize(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.certificateRequest(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
        }
    }

    @Test
    fun givenCertEnrollForNewClient_whenEnrolling_thenUpdateSelfUserInfo() = coroutineScope.runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange(coroutineScope) {
            withInitializingE2EIClientSucceed()
            withLoadTrustAnchorsResulting(Either.Right(Unit))
            withFetchFederationCertificateChainResulting(Either.Right(Unit))
            withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
            withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
            withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
            withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
            withGettingChallenges(Either.Right(AUTHORIZATIONS))
            withSelfUserFetched(false)
            withFetchSelfUser(Unit.right())
        }

        val result = enrollE2EICertificateUseCase.initialEnrollment(true)

        assertIs<InitialEnrollmentResult.Success>(result)
        advanceUntilIdle()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchSelfUser()
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val userRepository: UserRepository = mock()
        val e2EIRepository: E2EIRepository = mock()
        val conversationRepository: ConversationRepository = mock()

        private var selfUserFetched: Boolean = false

        suspend fun withFetchSelfUser(result: Either<CoreFailure, Unit>) {
            everySuspend {
                userRepository.fetchSelfUser()
            } calls {
                selfUserFetched = true
                result
            }
        }

        fun withSelfUserFetched(isFetched: Boolean = true) {
            selfUserFetched = isFetched
        }

        suspend fun withInitializingE2EIClientSucceed() = apply {
            everySuspend {
                e2EIRepository.initFreshE2EIClient(any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling initFreshE2EIClient" }
                Either.Right(Unit)
            }
        }

        suspend fun withLoadTrustAnchorsResulting(result: Either<E2EIFailure, Unit>) = apply {
            everySuspend {
                e2EIRepository.fetchAndSetTrustAnchors()
            } returns result
        }

        suspend fun withFetchFederationCertificateChainResulting(result: Either<E2EIFailure, Unit>) = apply {
            everySuspend {
                e2EIRepository.fetchFederationCertificates()
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling fetchFederationCertificates" }
                result
            }
        }

        suspend fun withLoadACMEDirectoriesResulting(result: Either<E2EIFailure, AcmeDirectory>) = apply {
            everySuspend {
                e2EIRepository.loadACMEDirectories()
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling loadACMEDirectories" }
                result
            }
        }

        suspend fun withGetACMENonceResulting(result: Either<E2EIFailure, Nonce>) = apply {
            everySuspend {
                e2EIRepository.getACMENonce(any())
            } returns result
        }

        suspend fun withCreateNewAccountResulting(result: Either<E2EIFailure, Nonce>) = apply {
            everySuspend {
                e2EIRepository.createNewAccount(any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling createNewAccount" }
                result
            }
        }

        suspend fun withCreateNewOrderResulting(result: Either<E2EIFailure, Triple<NewAcmeOrder, Nonce, String>>) = apply {
            everySuspend {
                e2EIRepository.createNewOrder(any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling createNewOrder" }
                result
            }
        }

        suspend fun withGettingChallenges(result: Either<E2EIFailure, AuthorizationResult>) = apply {
            everySuspend {
                e2EIRepository.getAuthorizations(any(), any())
            } returns result
        }

        suspend fun withGetWireNonceResulting(result: Either<E2EIFailure, Nonce>) = apply {
            everySuspend {
                e2EIRepository.getWireNonce()
            } returns result
        }

        suspend fun withGetDPoPTokenResulting(result: Either<E2EIFailure, String>) = apply {
            everySuspend {
                e2EIRepository.getDPoPToken(any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling getDPoPToken" }
                result
            }
        }

        suspend fun withGetWireAccessTokenResulting(result: Either<E2EIFailure, AccessTokenResponse>) = apply {
            everySuspend {
                e2EIRepository.getWireAccessToken(any())
            } returns result
        }

        suspend fun withValidateDPoPChallengeResulting(result: Either<E2EIFailure, ChallengeResponse>) = apply {
            everySuspend {
                e2EIRepository.validateDPoPChallenge(any(), any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling validateDPoPChallenge" }
                result
            }
        }

        suspend fun withValidateOIDCChallengeResulting(result: Either<E2EIFailure, ChallengeResponse>) = apply {
            everySuspend {
                e2EIRepository.validateOIDCChallenge(any(), any(), any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling validateOIDCChallenge" }
                result
            }
        }

        suspend fun withCheckOrderRequestResulting(result: Either<E2EIFailure, Pair<ACMEResponse, String>>) = apply {
            everySuspend {
                e2EIRepository.checkOrderRequest(any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling checkOrderRequest" }
                result
            }
        }

        suspend fun withFinalizeResulting(result: Either<E2EIFailure, Pair<ACMEResponse, String>>) = apply {
            everySuspend {
                e2EIRepository.finalize(any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling finalize" }
                result
            }
        }

        suspend fun withRotateKeysAndMigrateConversations(result: Either<E2EIFailure, Unit>) = apply {
            everySuspend {
                e2EIRepository.rotateKeysAndMigrateConversations(any(), any(), any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling rotateKeysAndMigrateConversations" }
                result
            }
        }

        suspend fun withCertificateRequestResulting(result: Either<E2EIFailure, ACMEResponse>) = apply {
            everySuspend {
                e2EIRepository.certificateRequest(any(), any())
            } calls {
                require(selfUserFetched) { "Self user should be fetched before calling certificateRequest" }
                result
            }
        }

        suspend fun withObserveConversationListResulting(result: List<Conversation>) = apply {
            everySuspend {
                conversationRepository.observeConversationList()
            } returns flowOf(result)
        }

        suspend fun arrange(coroutineScope: CoroutineScope, block: suspend Arrangement.() -> Unit): Pair<Arrangement, EnrollE2EIUseCase> =
            apply {
                withMLSTransactionReturning(Either.Right(Unit))
                block()
            }.let {
                this to EnrollE2EIUseCaseImpl(
                    e2EIRepository = e2EIRepository,
                    userRepository = userRepository,
                    coroutineScope = coroutineScope,
                    conversationRepository = conversationRepository,
                    transactionProvider = cryptoTransactionProvider
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
            dPopAuthorizations = DPOP_AUTHZ,
            oidcAuthorizations = OIDC_AUTHZ,
            oAuthClaims = OAUTH_CLAIMS,
            lastNonce = RANDOM_NONCE,
            orderLocation = RANDOM_LOCATION
        )
    }
}
