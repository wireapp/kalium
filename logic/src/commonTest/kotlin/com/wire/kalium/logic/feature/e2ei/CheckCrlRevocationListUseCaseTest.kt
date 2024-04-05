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
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CheckCrlRevocationListUseCaseTest {

    @Test
    fun givenExpiredCRL_whenTimeElapses_thenCheckRevocationList() = runTest {
        val (arrangement, checkCrlWorker) = Arrangement()
            .withExpiredCRL()
            .withCheckRevocationListResult()
            .arrange()

        checkCrlWorker(false)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::getCRLs)
            .wasInvoked(exactly = once)

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(eq(DUMMY_URL))
            .wasInvoked(exactly = once)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
            .with(eq(DUMMY_URL), eq(FUTURE_TIMESTAMP))
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenForceIsTrue_thenCheckRevicationEvenIfTimeDidnotElapse() = runTest {
        val (arrangement, checkCrlWorker) = Arrangement()
            .withNonExpiredCRL()
            .withCheckRevocationListResult()
            .arrange()

        checkCrlWorker(true)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::getCRLs)
            .wasInvoked(exactly = once)

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(eq(DUMMY_URL))
            .wasInvoked(exactly = once)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
            .with(eq(DUMMY_URL), eq(FUTURE_TIMESTAMP))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val certificateRevocationListRepository = mock(classOf<CertificateRevocationListRepository>())

        @Mock
        val checkRevocationList = mock(classOf<CheckRevocationListUseCase>())

        fun arrange() = this to CheckCrlRevocationListUseCase(
            certificateRevocationListRepository, checkRevocationList, kaliumLogger
        )

        fun withNoCRL() = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getCRLs)
                .whenInvoked()
                .thenReturn(null)
        }

        fun withNonExpiredCRL() = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getCRLs)
                .whenInvoked()
                .thenReturn(CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, FUTURE_TIMESTAMP))))
        }

        fun withExpiredCRL() = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getCRLs)
                .whenInvoked()
                .thenReturn(CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP))))
        }
        fun withCheckRevocationListResult() = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(FUTURE_TIMESTAMP))
        }
    }

    companion object {
        const val DUMMY_URL = "https://dummy.url"
        val TIMESTAMP = 633218892.toULong() // Wednesday, 24 January 1990 22:08:12
        val FUTURE_TIMESTAMP = 4104511692.toULong() // Sunday, 24 January 2100 22:08:12
    }
}
