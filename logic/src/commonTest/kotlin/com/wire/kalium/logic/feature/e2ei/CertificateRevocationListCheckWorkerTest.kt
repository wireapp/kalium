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

import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.persistence.config.CRLUrlExpirationList
import com.wire.kalium.persistence.config.CRLWithExpiration
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CertificateRevocationListCheckWorkerTest {

    @Test
    fun givenExpiredCRL_whenTimeElapses_thenCheckRevocationList() = runTest {
        val (arrangement, checkCrlWorker) = Arrangement()
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withExpiredCRL()
            .withCheckRevocationListResult()
            .arrange()

        checkCrlWorker()

        coVerify {
            arrangement.certificateRevocationListRepository.getCRLs()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.checkRevocationList.check(eq(DUMMY_URL))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(eq(DUMMY_URL), eq(FUTURE_TIMESTAMP))
        }.wasInvoked(exactly = once)

    }

    private class Arrangement {

        val certificateRevocationListRepository = mock(CertificateRevocationListRepository::class)
        val incrementalSyncRepository = mock(IncrementalSyncRepository::class)
        val checkRevocationList = mock(RevocationListChecker::class)

        fun arrange() = this to SyncCertificateRevocationListUseCase(
            certificateRevocationListRepository, incrementalSyncRepository, checkRevocationList, kaliumLogger
        )

        suspend fun withNoCRL() = apply {
            coEvery {
                certificateRevocationListRepository.getCRLs()
            }.returns(null)
        }

        suspend fun withNonExpiredCRL() = apply {
            coEvery {
                certificateRevocationListRepository.getCRLs()
            }.returns(CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, FUTURE_TIMESTAMP))))
        }

        suspend fun withExpiredCRL() = apply {
            coEvery {
                certificateRevocationListRepository.getCRLs()
            }.returns(CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP))))
        }
        suspend fun withCheckRevocationListResult() = apply {
            coEvery {
                checkRevocationList.check(any())
            }.returns(Either.Right(FUTURE_TIMESTAMP))
        }

        fun withIncrementalSyncState(flow: Flow<IncrementalSyncStatus>) = apply {
            every { incrementalSyncRepository.incrementalSyncState }
                .returns(flow)
        }

    }

    companion object {
        const val DUMMY_URL = "https://dummy.url"
        val TIMESTAMP = 633218892.toULong() // Wednesday, 24 January 1990 22:08:12
        val FUTURE_TIMESTAMP = 4104511692.toULong() // Sunday, 24 January 2100 22:08:12
    }
}
