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

import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.arrangement.repository.E2EIRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.E2EIRepositoryArrangementImpl
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ACMECertificatesSyncWorkerTest {

    @Test
    fun givenWorkerExecuted_whenDayPassed_thenSyncCalledAgain() = runTest {
        val (arrangement, worker) = arrange {
            withFetchACMECertificates()
        }
        val job = launch { worker.execute() }

        advanceTimeBy(arrangement.syncInterval.inWholeMilliseconds + 10)

        verify(arrangement.e2eiRepository)
            .suspendFunction(arrangement.e2eiRepository::fetchFederationCertificates)
            .wasInvoked(exactly = twice) // first on start and second after interval passed

        job.cancel()
    }

    private class Arrangement(
        private val configure: Arrangement.() -> Unit
    ) : E2EIRepositoryArrangement by E2EIRepositoryArrangementImpl() {

        var syncInterval: Duration = 1.minutes

        fun arrange(): Pair<Arrangement, ACMECertificatesSyncWorker> = run {
            configure()
            this@Arrangement to ACMECertificatesSyncWorkerImpl(
                e2eiRepository = e2eiRepository,
                syncInterval = syncInterval,
                kaliumLogger = kaliumLogger
            )
        }
    }

    private companion object {
        fun arrange(configure: Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
