/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.functional.intervalFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Worker that periodically syncs ACME Certificates.
 */
internal interface ACMECertificatesSyncWorker {
    suspend fun execute()
}

internal class ACMECertificatesSyncWorkerImpl(
    private val e2eiRepository: E2EIRepository,
    private val syncInterval: Duration = DEFAULT_SYNC_INTERVAL,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
    kaliumLogger: KaliumLogger
) : ACMECertificatesSyncWorker {

    private val logger = kaliumLogger.withTextTag("ACMECertificatesSyncWorker")

    override suspend fun execute() {
        logger.d("Starting to monitor")
        intervalFlow(syncInterval.inWholeMilliseconds)
            .collect {
                if (isE2EIEnabledUseCase()) {
                    logger.i("Fetching federation certificates")
                    e2eiRepository.fetchFederationCertificates()
                }
            }
    }

    private companion object {
        val DEFAULT_SYNC_INTERVAL = 1.days
    }
}
