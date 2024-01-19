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
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.functional.map
import kotlinx.datetime.Clock

/**
 * Use case to observe current client certificate and check if it is expired.
 */
interface ObserveCertificateForCurrentClientUseCase {
    suspend operator fun invoke()
}

internal class ObserveCertificateForCurrentClientUseCaseImpl(
    private val e2EIRepository: E2EIRepository,
    val userConfigRepository: UserConfigRepository,
    val checkRevocationList: CheckRevocationListUseCase
) : ObserveCertificateForCurrentClientUseCase {
    override suspend fun invoke() {
        e2EIRepository.getCurrentClientCrlUrl().map { url ->
            userConfigRepository.observeCertificateExpirationTime(url)
                .collect { certificateResult ->
                    certificateResult.map { expirationTime ->
                        // when the certificate is expired, check if the CRL the CRL and update the expiration time
                        if (expirationTime < Clock.System.now().epochSeconds.toULong()) {
                            checkRevocationList(url).map { newExpirationTime ->
                                newExpirationTime?.let {
                                    userConfigRepository.setCRLExpirationTime(url, it)
                                }
                            }
                        }
                    }
                }
        }
    }
}
