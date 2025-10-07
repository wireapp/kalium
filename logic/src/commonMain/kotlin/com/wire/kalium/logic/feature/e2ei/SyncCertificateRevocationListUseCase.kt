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

import com.wire.kalium.common.functional.map
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import io.mockative.Mockable
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * This use case will wait until the sync is done and then check the CRLs if needed.
 */
@Mockable
interface SyncCertificateRevocationListUseCase {
    suspend operator fun invoke()
}

/**
 * Base implementation of [SyncCertificateRevocationListUseCase].
 * @param certificateRevocationListRepository The CRL repository.
 * @param incrementalSyncRepository The incremental sync repository.
 * @param revocationListChecker The check revocation list use case.
 */
class SyncCertificateRevocationListUseCaseImpl internal constructor(
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val revocationListChecker: RevocationListChecker,
    private val transactionProvider: CryptoTransactionProvider,
    kaliumLogger: KaliumLogger
) : SyncCertificateRevocationListUseCase {

    private val logger = kaliumLogger.withTextTag("CertificateRevocationListCheckWorker")

    override suspend operator fun invoke() {
        logger.d("Starting to monitor")
        incrementalSyncRepository.incrementalSyncState
            .first { it is IncrementalSyncStatus.Live }
            .let {
                logger.i("Checking certificate revocation list (CRL)..")
                certificateRevocationListRepository.getCRLs()?.cRLWithExpirationList?.forEach { crl ->
                    if (crl.expiration < Clock.System.now().epochSeconds.toULong()) {
                        transactionProvider.mlsTransaction("SyncCertificateRevocationList") { mlsContext ->
                            revocationListChecker.check(mlsContext, crl.url).map { newExpirationTime ->
                                newExpirationTime?.let {
                                    certificateRevocationListRepository.addOrUpdateCRL(crl.url, it)
                                }
                            }
                        }
                    }
                }
            }
    }
}
