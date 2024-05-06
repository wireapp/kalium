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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Clock

/**
 * This worker will wait until the sync is done and then check the CRLs if needed.
 *
 */
interface CertificateRevocationListCheckWorker {
    suspend fun execute()
}

/**
 * Base implementation of [CertificateRevocationListCheckWorker].
 * @param certificateRevocationListRepository The CRL repository.
 * @param incrementalSyncRepository The incremental sync repository.
 * @param revocationListChecker The check revocation list use case.
 *
 */
internal class CertificateRevocationListCheckWorkerImpl(
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val revocationListChecker: RevocationListChecker,
    kaliumLogger: KaliumLogger
) : CertificateRevocationListCheckWorker {

    private val logger = kaliumLogger.withTextTag("CertificateRevocationListCheckWorker")

    override suspend fun execute() {
        logger.d("Starting to monitor")
        incrementalSyncRepository.incrementalSyncState
            .filter { it is IncrementalSyncStatus.Live }
            .collect {
                logger.i("Checking certificate revocation list (CRL)..")
                certificateRevocationListRepository.getCRLs()?.cRLWithExpirationList?.forEach { crl ->
                    if (crl.expiration < Clock.System.now().epochSeconds.toULong()) {
                        revocationListChecker.check(crl.url).map { newExpirationTime ->
                            newExpirationTime?.let {
                                certificateRevocationListRepository.addOrUpdateCRL(crl.url, it)
                            }
                        }
                    }
                }
            }
    }
}
