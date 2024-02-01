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
import com.wire.kalium.logic.data.e2ei.AuthorizationResult
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.Nonce
import com.wire.kalium.logic.feature.e2ei.usecase.E2EIEnrollmentResult
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
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
        arrangement.withLoadACMEDirectoriesResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::fetchAndSetTrustAnchors)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::loadACMEDirectories)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getACMENonce)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewAccount)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewOrder)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getAuthorizations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getOAuthRefreshToken).with().wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations).with().wasNotInvoked()
    }

    @Test
    fun givenGetACMENonceFails_whenInvokeUseCase_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::fetchAndSetTrustAnchors)
            .with()
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::loadACMEDirectories)
            .with()
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getACMENonce)
            .with(any<String>())
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewAccount)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewOrder)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getAuthorizations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getOAuthRefreshToken).with().wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenCreateNewAccountFails_whenInvokeUseCase_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewAccountResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::fetchAndSetTrustAnchors)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::loadACMEDirectories)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getACMENonce)
            .with(any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewAccount)
            .with(any<Nonce>(), any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewOrder)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getAuthorizations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getOAuthRefreshToken).with().wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCreateNewOrderFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewOrderResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::fetchAndSetTrustAnchors)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::loadACMEDirectories)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getACMENonce)
            .with(any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewAccount)
            .with(any<Nonce>(), any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewOrder)
            .with(any<Nonce>(), any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getAuthorizations).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getOAuthRefreshToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCreateAuthorizationsFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
        arrangement.withLoadACMEDirectoriesResulting(Either.Right(ACME_DIRECTORIES))
        arrangement.withGetACMENonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewAccountResulting(Either.Right(RANDOM_NONCE))
        arrangement.withCreateNewOrderResulting(Either.Right(Triple(ACME_ORDER, RANDOM_NONCE, RANDOM_LOCATION)))
        arrangement.withGettingChallenges(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.initialEnrollment()

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::fetchAndSetTrustAnchors)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::loadACMEDirectories)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getACMENonce)
            .with(any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewAccount)
            .with(any<Nonce>(), any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::createNewOrder)
            .with(any<Nonce>(), any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getAuthorizations)
            .with(any<Nonce>(), any<List<String>>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getOAuthRefreshToken).with().wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenCallingInitialization_thenReturnInitializationResult() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        val expected = INITIALIZATION_RESULT

        // given
        arrangement.withInitializingE2EIClientSucceed()
        arrangement.withLoadTrustAnchorsResulting(Either.Right(Unit))
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

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::fetchAndSetTrustAnchors).with().wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::loadACMEDirectories).with().wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getACMENonce).with(any<String>()).wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::createNewAccount).with(any<Nonce>(), any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::createNewOrder).with(any<Nonce>(), any<String>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getAuthorizations).with(any<Nonce>(), any<List<String>>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getOAuthRefreshToken).with().wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getWireNonce).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getDPoPToken).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getWireAccessToken).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::validateDPoPChallenge).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::validateOIDCChallenge).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::checkOrderRequest).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::finalize).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::certificateRequest).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations).with().wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations).with().wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetWireNonceFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetDPoPTokenFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken)
            .with()
            .wasNotInvoked()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenGetWireAccessTokenFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenValidateDPoPChallengeFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
    }

    @Test
    fun givenUseCase_whenValidateOIDCChallengeFailing_thenReturnFailure() = runTest {
        val (arrangement, enrollE2EICertificateUseCase) = Arrangement().arrange()

        // given
        arrangement.withGetWireNonceResulting(Either.Right(RANDOM_NONCE))
        arrangement.withGetDPoPTokenResulting(Either.Right(RANDOM_DPoP_TOKEN))
        arrangement.withGetWireAccessTokenResulting(Either.Right(WIRE_ACCESS_TOKEN))
        arrangement.withValidateDPoPChallengeResulting(Either.Right(ACME_CHALLENGE_RESPONSE))
        arrangement.withValidateOIDCChallengeResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
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
        arrangement.withCheckOrderRequestResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge)).wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
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
        arrangement.withFinalizeResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge)).wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with()
            .wasNotInvoked()
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with()
            .wasNotInvoked()
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
        arrangement.withCertificateRequestResulting(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getWireNonce).with().wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getDPoPToken).with(any<Nonce>()).wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge)).wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge)).wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::checkOrderRequest).with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::finalize).with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::certificateRequest).with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository).function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations).with().wasNotInvoked()
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
        arrangement.withRotateKeysAndMigrateConversations(TEST_EITHER_LEFT)

        // when
        val result = enrollE2EICertificateUseCase.finalizeEnrollment(RANDOM_ID_TOKEN, REFRESH_TOKEN, INITIALIZATION_RESULT)

        // then
        result.shouldFail()

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)
        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with(any<String>(), any<Boolean>())
            .wasInvoked(exactly = once)
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

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireNonce)
            .with()
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getDPoPToken)
            .with(any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::getWireAccessToken).with(eq(RANDOM_DPoP_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateDPoPChallenge)
            .with(eq(WIRE_ACCESS_TOKEN.token), any<Nonce>(), eq(DPOP_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::validateOIDCChallenge)
            .with(eq(RANDOM_ID_TOKEN), eq(REFRESH_TOKEN), any<Nonce>(), eq(OIDC_AUTHZ.challenge))
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::checkOrderRequest)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::finalize)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::certificateRequest)
            .with(any<String>(), any<Nonce>())
            .wasInvoked(exactly = once)

        verify(arrangement.e2EIRepository)
            .function(arrangement.e2EIRepository::rotateKeysAndMigrateConversations)
            .with(any<String>(), any<Boolean>())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val e2EIRepository = mock(classOf<E2EIRepository>())

        fun withInitializingE2EIClientSucceed() = apply {
            given(e2EIRepository).suspendFunction(e2EIRepository::initFreshE2EIClient)
                .whenInvokedWith(anything(), anything() )
                .thenReturn(Either.Right(Unit))
        }

        fun withLoadTrustAnchorsResulting(result: Either<CoreFailure, Unit>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::fetchAndSetTrustAnchors)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withLoadACMEDirectoriesResulting(result: Either<CoreFailure, AcmeDirectory>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::loadACMEDirectories)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withGetACMENonceResulting(result: Either<CoreFailure, Nonce>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getACMENonce)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withCreateNewAccountResulting(result: Either<CoreFailure, Nonce>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::createNewAccount)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withCreateNewOrderResulting(result: Either<CoreFailure, Triple<NewAcmeOrder, Nonce, String>>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::createNewOrder)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withGettingChallenges(result: Either<CoreFailure, AuthorizationResult>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getAuthorizations)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withGettingRefreshTokenSucceeding() = apply {
            given(e2EIRepository).suspendFunction(e2EIRepository::getOAuthRefreshToken).whenInvoked()
                .thenReturn(Either.Right(REFRESH_TOKEN))
        }

        fun withGetWireNonceResulting(result: Either<CoreFailure, Nonce>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getWireNonce)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withGetDPoPTokenResulting(result: Either<CoreFailure, String>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getDPoPToken)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withGetWireAccessTokenResulting(result: Either<CoreFailure, AccessTokenResponse>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getWireAccessToken)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withValidateDPoPChallengeResulting(result: Either<CoreFailure, ChallengeResponse>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::validateDPoPChallenge)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        fun withValidateOIDCChallengeResulting(result: Either<CoreFailure, ChallengeResponse>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::validateOIDCChallenge)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        fun withCheckOrderRequestResulting(result: Either<CoreFailure, Pair<ACMEResponse, String>>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::checkOrderRequest)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withFinalizeResulting(result: Either<CoreFailure, Pair<ACMEResponse, String>>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::finalize)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withRotateKeysAndMigrateConversations(result: Either<CoreFailure, Unit>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::rotateKeysAndMigrateConversations)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withCertificateRequestResulting(result: Either<CoreFailure, ACMEResponse>) = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::certificateRequest)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun arrange(): Pair<Arrangement, EnrollE2EIUseCase> = this to EnrollE2EIUseCaseImpl(e2EIRepository)
    }

    companion object {
        val RANDOM_ID_TOKEN = "idToken"
        val RANDOM_DPoP_TOKEN = "dpopToken"
        val RANDOM_NONCE = Nonce("random-nonce")
        val REFRESH_TOKEN = "YRjxLpsjRqL7zYuKstXogqioA_P3Z4fiEuga0NCVRcDSc8cy_9msxg"
        val TEST_CORE_FAILURE = CoreFailure.Unknown(Throwable("an error"))
        val TEST_EITHER_LEFT = Either.Left(TEST_CORE_FAILURE)
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
