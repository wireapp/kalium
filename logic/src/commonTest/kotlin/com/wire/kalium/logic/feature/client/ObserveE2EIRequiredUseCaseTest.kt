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

import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.feature.e2ei.MLSClientE2EIStatus
import com.wire.kalium.logic.feature.e2ei.MLSClientIdentity
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCase
import com.wire.kalium.logic.feature.user.E2EIRequiredResult
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestMLSClientIdentity.getMLSClientIdentityWithE2EI
import com.wire.kalium.logic.framework.TestMLSClientIdentity.getMLSClientIdentityWithOutE2EI
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.DateTimeUtil
import io.mockative.any
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveE2EIRequiredUseCaseTest {

    @Test
    fun givenSettingWithNotifyDateInPast_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant().plus(2.days)
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(getMLSClientIdentityWithOutE2EI(CLIENT_ID_USER1).right())
            .arrange()

        useCase().test {
            assertTrue { awaitItem() is E2EIRequiredResult.WithGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDeadlineInPast_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(getMLSClientIdentityWithOutE2EI(CLIENT_ID_USER1).right())
            .arrange()

        useCase().test {
            assertEquals(E2EIRequiredResult.NoGracePeriod.Create, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInFuture_thenEmitResultWithDelay() = runTest(TestKaliumDispatcher.io) {
        val delayDuration = 10.minutes
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant().plus(delayDuration))
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(getMLSClientIdentityWithOutE2EI(CLIENT_ID_USER1).right())
            .arrange()

        useCase().test {
            advanceTimeBy(delayDuration.minus(1.minutes).inWholeMilliseconds)
            expectNoEvents()

            advanceTimeBy(delayDuration.inWholeMilliseconds)
            assertEquals(E2EIRequiredResult.NoGracePeriod.Create, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPast_thenEmitResultWithoutDelay() = runTest(TestKaliumDispatcher.io) {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(getMLSClientIdentityWithOutE2EI(CLIENT_ID_USER1).right())
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertEquals(E2EIRequiredResult.NoGracePeriod.Create, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDisabledStatus_thenNoEmitting() = runTest {
        val setting = MLS_E2EI_SETTING.copy(isRequired = false)
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(getMLSClientIdentityWithOutE2EI(CLIENT_ID_USER1).right())
            .arrange()

        useCase().test {
            assertTrue { awaitItem() == E2EIRequiredResult.NotRequired }
            awaitComplete()
        }
    }

    @Test
    fun givenMLSFeatureIsDisabled_thenNotRequiredIsEmitted() = runTest {
        val delayDuration = 10.minutes
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (arrangement, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant().plus(delayDuration))
            .withIsMLSSupported(false)
            .withCurrentClientProviderSuccess()
            .arrange()

        useCase().test {
            advanceUntilIdle()
            assertTrue { awaitItem() == E2EIRequiredResult.NotRequired }
            awaitComplete()
        }

        verify {
            arrangement.userConfigRepository.observeE2EINotificationTime()
        }.wasNotInvoked()
    }

    @Test
    fun givenSettingWithNotifyDateInPastAndUserHasCertificate_thenEmitRenewResult() = runTest(TestKaliumDispatcher.io) {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val mlsClientIdentity = getMLSClientIdentityWithE2EI(CLIENT_ID_USER1)
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(
                mlsClientIdentity.copy(x509Identity = mlsClientIdentity.x509Identity?.copy(notAfter = Instant.DISTANT_PAST)).right()
            )
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertTrue(awaitItem() is E2EIRequiredResult.WithGracePeriod.Renew)
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPastAndUserHasExpiredCertificate_thenEmitRequiredResult() = runTest(TestKaliumDispatcher.io) {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(
                getMLSClientIdentityWithE2EI(CLIENT_ID_USER1, MLSClientE2EIStatus.EXPIRED).right()
            )
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertEquals(E2EIRequiredResult.NoGracePeriod.Renew, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPastAndGettingUserCertificateFail_thenNotRequired() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant().plus(2.days)
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(StorageFailure.DataNotFound.left())
            .arrange()

        useCase().test {
            assertTrue { awaitItem() is E2EIRequiredResult.NotRequired }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPastAndUserHasEndedCertificate_thenEmitRenewResult() = runTest(TestKaliumDispatcher.io) {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(
                getMLSClientIdentityWithE2EI(CLIENT_ID_USER1, MLSClientE2EIStatus.EXPIRED).right()
            )
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertIs<E2EIRequiredResult.NoGracePeriod.Renew>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPastAndUserHasValidCertificate_thenEmitNotRequiredResult() = runTest(TestKaliumDispatcher.io) {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(
                getMLSClientIdentityWithE2EI(CLIENT_ID_USER1).right()
            )
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertEquals(E2EIRequiredResult.NotRequired, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenCertRevoked_thenReturnNotRequired() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .withCurrentClientProviderSuccess()
            .withGetE2EICertificateUseCaseResult(
                getMLSClientIdentityWithE2EI(CLIENT_ID_USER1, MLSClientE2EIStatus.REVOKED).right()
            )
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertIs<E2EIRequiredResult.NotRequired>(awaitItem())
            awaitComplete()
        }
    }

    private class Arrangement(testDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) {
                val userConfigRepository = mock(UserConfigRepository::class)
        val featureSupport = mock(FeatureSupport::class)
        val e2eiCertificate = mock(GetMLSClientIdentityUseCase::class)
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        private var observeMLSEnabledUseCase: ObserveE2EIRequiredUseCase =
            ObserveE2EIRequiredUseCaseImpl(userConfigRepository, featureSupport, e2eiCertificate, currentClientIdProvider, testDispatcher)

        fun withMLSE2EISetting(setting: E2EISettings) = apply {
            every { userConfigRepository.observeE2EISettings() }
                .returns(flowOf(Either.Right(setting)))
        }

        fun withE2EINotificationTime(instant: Instant) = apply {
            every { userConfigRepository.observeE2EINotificationTime() }
                .returns(flowOf(Either.Right(instant)))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(supported)
        }

        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        suspend fun withGetE2EICertificateUseCaseResult(result: Either<CoreFailure, MLSClientIdentity>) = apply {
            coEvery {
                e2eiCertificate.invoke(any())
            }.returns(result)
        }

        fun arrange() = this to observeMLSEnabledUseCase
    }

    companion object {
        private val MLS_E2EI_SETTING = E2EISettings(true, "some_url", null, false, null)
        private val CLIENT_ID_USER1 = QualifiedClientID(ClientId("clientId1"), TestUser.USER_ID)
    }
}
