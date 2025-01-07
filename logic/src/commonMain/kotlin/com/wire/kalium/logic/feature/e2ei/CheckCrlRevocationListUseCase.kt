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
import com.wire.kalium.logic.functional.map
import kotlinx.datetime.Clock

/**
 * Use case to check the certificate revocation list (CRL) for expired entries.
 * param forceUpdate: if true, the CRL will be checked even if it is not expired.
 */
class CheckCrlRevocationListUseCase internal constructor(
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val revocationListChecker: RevocationListChecker,
    kaliumLogger: KaliumLogger
) {

    private val logger = kaliumLogger.withTextTag("CheckCrlRevocationListUseCase")

    suspend operator fun invoke(forceUpdate: Boolean) {
        logger.i("Checking certificate revocation list (CRL). Force update: $forceUpdate")
        certificateRevocationListRepository.getCRLs()?.cRLWithExpirationList?.forEach { crl ->
            if (forceUpdate || (crl.expiration < Clock.System.now().epochSeconds.toULong())) {
                revocationListChecker.check(crl.url).map { newExpirationTime ->
                    newExpirationTime?.let {
                        certificateRevocationListRepository.addOrUpdateCRL(crl.url, it)
                    }
                }
            }
        } ?: logger.w("No CRLs found.")
    }
}
