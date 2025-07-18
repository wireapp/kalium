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
import io.mockative.Mockable

/**
 * Use case that syncs ACME Certificates.
 */
@Mockable
internal interface ACMECertificatesSyncUseCase {
    suspend operator fun invoke()
}

internal class ACMECertificatesSyncUseCaseImpl(
    private val e2eiRepository: E2EIRepository,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
    kaliumLogger: KaliumLogger
) : ACMECertificatesSyncUseCase {

    private val logger = kaliumLogger.withTextTag("ACMECertificatesSyncWorker")

    override suspend operator fun invoke() {
        if (isE2EIEnabledUseCase()) {
            logger.i("Fetching federation certificates")
            e2eiRepository.fetchFederationCertificates()
        }
    }
}
