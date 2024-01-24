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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.e2ei.CrlRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.config.CRLUrlExpirationList
import com.wire.kalium.persistence.config.CRLWithExpiration
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

class CheckCrlWorkerTest {

    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_whenWorkerIsRunning_thenShouldNotCheckCRLsExpiration() =
        runTest {
            val lastCheck = Clock.System.now() - 23.hours
            val (arrangement, checkCrlWorker) = Arrangement()
                .withObserveLastCRLCheckInstantReturning(flowOf(lastCheck))
                .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
                .withSetLastCRLCheckInstantReturning(Either.Right(Unit))
                .withNoCRL()
                .arrange()

            launch {
                checkCrlWorker.execute()
            }

            verify(arrangement.crlRepository)
                .suspendFunction(arrangement.crlRepository::getCRLs)
                .wasNotInvoked()
        }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenWorkerIsRunning_thenShouldCheckCRLsExpiration() = runTest {
        val lastCheck = Clock.System.now() - 24.hours
        val (arrangement, checkCrlWorker) = Arrangement()
            .withObserveLastCRLCheckInstantReturning(flowOf(lastCheck))
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withSetLastCRLCheckInstantReturning(Either.Right(Unit))
            .withNonExpiredCRL()
            .arrange()

        checkCrlWorker.execute()

        verify(arrangement.crlRepository)
            .suspendFunction(arrangement.crlRepository::getCRLs)
            .wasInvoked(exactly = once)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_whenTimeElapses_thenShouldCheckCRLsExpiration() = runTest {
        val lastCheck = Clock.System.now() - 23.hours
        val (arrangement, checkCrlWorker) = Arrangement()
            .withObserveLastCRLCheckInstantReturning(flowOf(lastCheck))
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withSetLastCRLCheckInstantReturning(Either.Right(Unit))
            .withNonExpiredCRL()
            .arrange()

        launch {
            checkCrlWorker.execute()
        }

        verify(arrangement.crlRepository)
            .suspendFunction(arrangement.crlRepository::getCRLs)
            .wasNotInvoked()

        // Advance time until it's time to refill
        advanceTimeBy(2.hours)

        verify(arrangement.crlRepository)
            .suspendFunction(arrangement.crlRepository::getCRLs)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenExpiredCRL_whenTimeElapses_thenCheckRevocationList() = runTest {
        val lastCheck = Clock.System.now() - 24.hours
        val (arrangement, checkCrlWorker) = Arrangement()
            .withObserveLastCRLCheckInstantReturning(flowOf(lastCheck))
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withSetLastCRLCheckInstantReturning(Either.Right(Unit))
            .withExpiredCRL()
            .withCheckRevocationListResult()
            .arrange()

        checkCrlWorker.execute()

        verify(arrangement.crlRepository)
            .suspendFunction(arrangement.crlRepository::getCRLs)
            .wasInvoked(exactly = once)

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(eq(DUMMY_URL))
            .wasInvoked(exactly = once)

        verify(arrangement.crlRepository)
            .suspendFunction(arrangement.crlRepository::addOrUpdateCRL)
            .with(eq(DUMMY_URL), eq(FUTURE_TIMESTAMP))
            .wasInvoked(exactly = once)

    }

    private class Arrangement {

        @Mock
        val crlRepository = mock(classOf<CrlRepository>())

        @Mock
        val incrementalSyncRepository = mock(classOf<IncrementalSyncRepository>())

        @Mock
        val checkRevocationList = mock(classOf<CheckRevocationListUseCase>())

        fun arrange() = this to CheckCrlWorkerImpl(
            crlRepository, incrementalSyncRepository, checkRevocationList
        )

        fun withNoCRL() = apply {
            given(crlRepository)
                .suspendFunction(crlRepository::getCRLs)
                .whenInvoked()
                .thenReturn(null)
        }

        fun withNonExpiredCRL() = apply {
            given(crlRepository)
                .suspendFunction(crlRepository::getCRLs)
                .whenInvoked()
                .thenReturn(CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, FUTURE_TIMESTAMP))))
        }

        fun withExpiredCRL() = apply {
            given(crlRepository)
                .suspendFunction(crlRepository::getCRLs)
                .whenInvoked()
                .thenReturn(CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP))))
        }
        fun withCheckRevocationListResult() = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(FUTURE_TIMESTAMP))
        }

        fun withObserveLastCRLCheckInstantReturning(flow: Flow<Instant?>) = apply {
            given(crlRepository)
                .suspendFunction(crlRepository::lastCrlCheckInstantFlow)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun withIncrementalSyncState(flow: Flow<IncrementalSyncStatus>) = apply {
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::incrementalSyncState)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun withSetLastCRLCheckInstantReturning(result: Either<StorageFailure, Unit>) = apply {
            given(crlRepository)
                .suspendFunction(crlRepository::setLastCRLCheckInstant)
                .whenInvokedWith(any())
                .thenReturn(result)
        }
    }

    companion object {
        const val DUMMY_URL = "https://dummy.url"
        val TIMESTAMP = 633218892.toULong() // Wednesday, 24 January 1990 22:08:12
        val FUTURE_TIMESTAMP = 4104511692.toULong() // Sunday, 24 January 2100 22:08:12
    }
}
