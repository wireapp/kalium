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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ACMECertificatesSyncUseCaseTest {

    @Test
    fun givenDailyWorkerRuns_whenE2EIIsEnabled_thenRefreshesFederationCertificatesAndChecksCredentials() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withE2EIEnabledAndMLSEnabled(true)
            withPkiRefreshSuccessful()
        }

        // when
        useCase()

        // then
        verifySuspend(VerifyMode.not) {
            arrangement.e2eiRepository.fetchAndAddTrustAnchors()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2eiRepository.fetchFederationCertificates()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2eiRepository.checkCredentials()
        }
    }

    @Test
    fun givenDailyWorkerRuns_whenE2EIIsDisabled_thenPkiStateIsNotTouched() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withE2EIEnabledAndMLSEnabled(false)
        }

        // when
        useCase()

        // then
        verifySuspend(VerifyMode.not) {
            arrangement.e2eiRepository.fetchAndAddTrustAnchors()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2eiRepository.fetchFederationCertificates()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.e2eiRepository.checkCredentials()
        }
    }

    @Test
    fun givenPkiRefreshFails_whenDailyWorkerRuns_thenEachFailureIsLogged() = runTest {
        val logWriter = RecordingLogWriter()
        val (_, useCase) = arrange(recordingLogger(logWriter)) {
            withE2EIEnabledAndMLSEnabled(true)
            withPkiRefreshFailures()
        }

        useCase()

        val warningMessages = logWriter.entries
            .filter { it.severity == Severity.Warn }
            .map { it.message }
        assertEquals(2, warningMessages.size)
        assertContains(warningMessages[0], "Refreshing PKI federation certificates failed")
        assertContains(warningMessages[1], "Checking installed X.509 credentials failed")
    }

    private class Arrangement(
        private val logger: KaliumLogger,
        private val configure: suspend Arrangement.() -> Unit
    ) {
        val e2eiRepository = mock<E2EIRepository>(mode = MockMode.autoUnit)
        val isE2EIEnabledUseCase = mock<IsE2EIEnabledUseCase>(mode = MockMode.autoUnit)

        suspend fun arrange(): Pair<Arrangement, ACMECertificatesSyncUseCase> = run {
            configure()
            this@Arrangement to ACMECertificatesSyncUseCaseImpl(
                e2eiRepository = e2eiRepository,
                isE2EIEnabledUseCase = isE2EIEnabledUseCase,
                kaliumLogger = logger
            )
        }

        suspend fun withE2EIEnabledAndMLSEnabled(result: Boolean) {
            everySuspend { isE2EIEnabledUseCase.invoke() } returns result
        }

        suspend fun withPkiRefreshSuccessful() {
            everySuspend { e2eiRepository.fetchFederationCertificates() } returns Either.Right(Unit)
            everySuspend { e2eiRepository.checkCredentials() } returns Either.Right(Unit)
        }

        suspend fun withPkiRefreshFailures() {
            val failure: Either<E2EIFailure, Unit> = Either.Left(
                E2EIFailure.Generic(IllegalStateException("refresh failed"))
            )
            everySuspend { e2eiRepository.fetchFederationCertificates() } returns failure
            everySuspend { e2eiRepository.checkCredentials() } returns failure
        }
    }

    private class RecordingLogWriter : LogWriter() {
        val entries = mutableListOf<LogEntry>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += LogEntry(severity, message)
        }
    }

    private data class LogEntry(val severity: Severity, val message: String)

    private companion object {
        suspend fun arrange(
            logger: KaliumLogger = kaliumLogger,
            configure: suspend Arrangement.() -> Unit
        ) = Arrangement(logger, configure).arrange()

        fun recordingLogger(logWriter: LogWriter) = KaliumLogger(
            config = KaliumLogger.Config(
                initialLevel = KaliumLogLevel.DEBUG,
                initialLogWriterList = listOf(logWriter)
            ),
            tag = "ACMECertificatesSyncUseCaseTest"
        )
    }
}
