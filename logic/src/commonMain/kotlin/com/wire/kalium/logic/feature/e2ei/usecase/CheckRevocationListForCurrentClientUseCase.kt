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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.feature.e2ei.CertificateRevocationListCheckWorker
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

/**
 * Use case to check the revocation list for the current client and store it in the DB.
 * This will run once as [CertificateRevocationListCheckWorker] is constantly checking the CRLs that are stored in DB.
 */
interface CheckRevocationListForCurrentClientUseCase {
    suspend operator fun invoke()
}

internal class CheckRevocationListForCurrentClientUseCaseImpl(
    private val checkRevocationList: CheckRevocationListUseCase,
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val userConfigRepository: UserConfigRepository,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase
) : CheckRevocationListForCurrentClientUseCase {
    override suspend fun invoke() {
        if (isE2EIEnabledUseCase() && userConfigRepository.shouldCheckCrlForCurrentClient()) {
            certificateRevocationListRepository.getCurrentClientCrlUrl().onSuccess { url ->
                kaliumLogger.i("Checking CRL for current client..")
                checkRevocationList(url)
                    .onSuccess { expiration ->
                        kaliumLogger.i("Successfully checked CRL for current client..")
                        expiration?.let {
                            certificateRevocationListRepository.addOrUpdateCRL(url, it)
                            userConfigRepository.setShouldCheckCrlForCurrentClient(false)
                        }
                    }
                    .onFailure {
                        kaliumLogger.i("Failed to check CRL for current client..")
                    }
            }
        }
    }
}
