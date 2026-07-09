/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SyncMeetingsUseCaseTest {

    @Test
    fun givenMeetingFeatureNotSupported_whenInvoking_thenSkipAndReturnUnit() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMeetingSupportEnabled(false)
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.not) { arrangement.meetingRepository.fetchAndPersistMeetings() }
    }

    @Test
    fun givenMeetingFeatureReturnsFeatureNotSupported_whenInvoking_thenSkipAndReturnUnit() = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingSupportEnabled(true)
            .withFetchMeetingsFailed(NetworkFailure.FeatureNotSupported)
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenMeetingFeatureReturnsOtherFailure_whenInvoking_thenReturnFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingSupportEnabled(true)
            .withFetchMeetingsFailed(NetworkFailure.NoNetworkConnection(null))
            .arrange()

        val result = useCase()

        assertIs<Either.Left<NetworkFailure.NoNetworkConnection>>(result)
    }

    @Test
    fun givenMeetingFeatureReturnsSuccess_whenInvoking_thenReturnUnit() = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingSupportEnabled(true)
            .withFetchMeetingsSuccessful()
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
    }

    inner class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        internal val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        internal val featureSupport = mock<FeatureSupport>(mode = MockMode.autoUnit)

        internal fun withMeetingSupportEnabled(enabled: Boolean) = apply {
            every { featureSupport.isMeetingsSupported } returns enabled
        }

        internal fun withFetchMeetingsFailed(failure: CoreFailure) = apply {
            everySuspend { meetingRepository.fetchAndPersistMeetings() } returns Either.Left(failure)
        }

        internal fun withFetchMeetingsSuccessful() = apply {
            everySuspend { meetingRepository.fetchAndPersistMeetings() } returns Either.Right(Unit)
        }

        internal suspend fun arrange() = this to SyncMeetingsUseCaseImpl(
            meetingRepository = meetingRepository,
            featureSupport = featureSupport,
            transactionProvider = cryptoTransactionProvider
        ).also {
            withTransactionReturning(Either.Right(Unit))
        }
    }
}
