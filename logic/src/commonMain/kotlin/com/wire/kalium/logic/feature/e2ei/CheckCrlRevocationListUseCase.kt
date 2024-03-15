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
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
import com.wire.kalium.logic.functional.map
import kotlinx.datetime.Clock

class CheckCrlRevocationListUseCase internal constructor(
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val checkRevocationList: CheckRevocationListUseCase,
    kaliumLogger: KaliumLogger
) : CertificateRevocationListCheckWorker {

    private val logger = kaliumLogger.withTextTag("CheckCrlRevocationListUseCase")

    override suspend fun execute() {
        logger.i("Checking certificate revocation list (CRL)..")
        certificateRevocationListRepository.getCRLs()?.cRLWithExpirationList?.forEach { crl ->
            if (crl.expiration < Clock.System.now().epochSeconds.toULong()) {
                checkRevocationList(crl.url).map { newExpirationTime ->
                    newExpirationTime?.let {
                        certificateRevocationListRepository.addOrUpdateCRL(crl.url, it)
                    }
                }
            }
        }
    }
}
