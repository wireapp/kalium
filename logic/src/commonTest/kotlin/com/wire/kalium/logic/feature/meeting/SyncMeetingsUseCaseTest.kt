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
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
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
    fun givenNoMeetingsFetched_whenInvoking_thenDoNotFetchUsers() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMeetingsEnabled(true)
            .withFetchMeetingsSuccessful(emptyList())
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.meetingRepository.fetchAndPersistMeetings() }
        verifySuspend(VerifyMode.not) { arrangement.userRepository.fetchUsersIfUnknownByIds(any()) }
    }

    @Test
    fun givenSuccess_whenInvoking_thenExecuteRequestsAndReturnUnit() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMeetingsEnabled(true)
            .withFetchMeetingsSuccessful(listOf(MEETING))
            .withFetchUsersSuccessful()
            .arrange()

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.meetingRepository.fetchAndPersistMeetings() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userRepository.fetchUsersIfUnknownByIds(setOf(MEETING.creatorId.toModel())) }
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

        internal fun withFetchMeetingsSuccessful(list: List<MeetingEntity>) = apply {
            everySuspend { meetingRepository.fetchAndPersistMeetings() } returns Either.Right(list)
        }

        internal fun withFetchUsersSuccessful() = apply {
            everySuspend { userRepository.fetchUsersIfUnknownByIds(any()) } returns Either.Right(Unit)
        }

        internal suspend fun arrange() = this to SyncMeetingsUseCaseImpl(
            meetingRepository = meetingRepository,
            userRepository = userRepository,
            isMeetingsEnabledUseCase = isMeetingsEnabledUseCase,
            transactionProvider = cryptoTransactionProvider
        ).also {
            withTransactionReturning(Either.Right(Unit))
        }
    }

    private val MEETING = MeetingEntity(
        meetingId = QualifiedIDEntity("meetingId", "doman"),
        conversationId = QualifiedIDEntity("conversationId", "domain"),
        creatorId = QualifiedIDEntity("creatorId", "domain"),
        createdAt = Instant.parse("2026-08-01T12:00:00.000Z"),
        updatedAt = null,
        title = "Meeting Title",
        startTime = Instant.parse("2026-08-01T12:00:00.000Z"),
        endTime = Instant.parse("2026-08-01T13:00:00.000Z"),
        trial = false,
        recurrence = null
    )
}
