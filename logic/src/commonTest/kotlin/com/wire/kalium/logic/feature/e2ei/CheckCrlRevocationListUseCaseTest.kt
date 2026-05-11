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
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.persistence.config.CRLUrlExpirationList
import com.wire.kalium.persistence.config.CRLWithExpiration
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.certificateRevocationListRepository.getCRLs()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.checkRevocationList.check(any(), eq(DUMMY_URL))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(eq(DUMMY_URL), eq(FUTURE_TIMESTAMP))
        }

    }

    @Test
    fun givenForceIsTrue_thenCheckRevicationEvenIfTimeDidnotElapse() = runTest {
        val (arrangement, checkCrlWorker) = Arrangement()
            .withNonExpiredCRL()
            .withCheckRevocationListResult()
            .arrange()

        checkCrlWorker(true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.certificateRevocationListRepository.getCRLs()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.checkRevocationList.check(any(), eq(DUMMY_URL))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(eq(DUMMY_URL), eq(FUTURE_TIMESTAMP))
        }
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val certificateRevocationListRepository: CertificateRevocationListRepository = mock(mode = MockMode.autoUnit)
        val checkRevocationList: RevocationListChecker = mock(mode = MockMode.autoUnit)

        suspend fun arrange() = this to CheckCrlRevocationListUseCase(
            certificateRevocationListRepository, checkRevocationList, cryptoTransactionProvider, kaliumLogger
        )
            .also {
                withMLSTransactionReturning(Either.Right(Unit))
            }

        suspend fun withNoCRL() = apply {
            everySuspend {
                certificateRevocationListRepository.getCRLs()
            } returns null
        }

        suspend fun withNonExpiredCRL() = apply {
            everySuspend {
                certificateRevocationListRepository.getCRLs()
            } returns CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, FUTURE_TIMESTAMP)))
        }

        suspend fun withExpiredCRL() = apply {
            everySuspend {
                certificateRevocationListRepository.getCRLs()
            } returns CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP)))
        }
        suspend fun withCheckRevocationListResult() = apply {
            everySuspend {
                checkRevocationList.check(any(), any())
            } returns Either.Right(FUTURE_TIMESTAMP)
        }
    }

    companion object {
        const val DUMMY_URL = "https://dummy.url"
        val TIMESTAMP = 633218892.toULong()
        val FUTURE_TIMESTAMP = 4104511692.toULong()
    }
}
