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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncMeetingsUseCaseTest {

    @Test
    fun givenMeetingsEnabled_whenCheckingIsEnabled_thenReturnTrue() = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingsEnabled(true)
            .arrange()

        val result = useCase.isEnabled()

        assertEquals(true, result)
    }

    @Test
    fun givenMeetingsDisabled_whenCheckingIsEnabled_thenReturnFalse() = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingsEnabled(false)
            .arrange()

        val result = useCase.isEnabled()

        assertEquals(false, result)
    }

    @Test
    fun givenMeetingsDisabled_whenInvoking_thenSkipAndReturnUnit() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMeetingsEnabled(false)
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.not) { arrangement.meetingRepository.fetchAndPersistMeetings() }
    }

    @Test
    fun givenFeatureNotSupportedFailure_whenInvoking_thenSkipAndReturnUnit() = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingsEnabled(true)
            .withFetchMeetingsFailed(NetworkFailure.FeatureNotSupported)
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenOtherFailure_whenInvoking_thenReturnFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingsEnabled(true)
            .withFetchMeetingsFailed(NetworkFailure.NoNetworkConnection(null))
            .arrange()

        val result = useCase()

        assertIs<Either.Left<NetworkFailure.NoNetworkConnection>>(result)
    }

    @Test
    fun givenSuccess_whenInvoking_thenExecuteRequestsAndReturnUnit() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMeetingsEnabled(true)
            .withFetchMeetingsSuccessful(listOf(MEETING))
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.meetingRepository.fetchAndPersistMeetings() }
    }

    inner class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        internal val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        internal val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        internal val isMeetingsEnabledUseCase = mock<IsMeetingsEnabledUseCase>(mode = MockMode.autoUnit)

        internal fun withMeetingsEnabled(enabled: Boolean) = apply {
            everySuspend { isMeetingsEnabledUseCase() } returns enabled
        }

        internal fun withFetchMeetingsFailed(failure: CoreFailure) = apply {
            everySuspend { meetingRepository.fetchAndPersistMeetings() } returns Either.Left(failure)
        }

        internal fun withFetchMeetingsSuccessful(list: List<Meeting>) = apply {
            everySuspend { meetingRepository.fetchAndPersistMeetings() } returns Either.Right(list)
        }

        internal suspend fun arrange() = this to SyncMeetingsUseCaseImpl(
            meetingRepository = meetingRepository,
            isMeetingsEnabledUseCase = isMeetingsEnabledUseCase,
            transactionProvider = cryptoTransactionProvider
        ).also {
            withTransactionReturning(Either.Right(Unit))
        }
    }

    private val MEETING = Meeting(
        meetingId = MeetingId("meetingId", "doman"),
        conversationId = ConversationId("conversationId", "domain"),
        creatorId = UserId("creatorId", "domain"),
        title = "Meeting Title",
        startTime = Instant.parse("2026-08-01T12:00:00.000Z"),
        endTime = Instant.parse("2026-08-01T13:00:00.000Z"),
        recurrence = null
    )
}
