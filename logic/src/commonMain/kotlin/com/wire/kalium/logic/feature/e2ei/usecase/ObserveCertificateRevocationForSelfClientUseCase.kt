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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.e2ei.MLSClientE2EIStatus
import com.wire.kalium.logic.functional.onSuccess

/**
 * Use case to observe certificate revocation for self client.
 */
interface ObserveCertificateRevocationForSelfClientUseCase {
    suspend operator fun invoke()
}

@Suppress("LongParameterList")
internal class ObserveCertificateRevocationForSelfClientUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val getE2eiCertificate: GetMLSClientIdentityUseCase,
    kaliumLogger: KaliumLogger,
) : ObserveCertificateRevocationForSelfClientUseCase {

    private val logger = kaliumLogger.withTextTag("ObserveCertificateRevocationForSelfClient")

    override suspend fun invoke() {
        logger.d("Checking if should notify certificate revocation")
        currentClientIdProvider().onSuccess { clientId ->
            getE2eiCertificate(clientId).onSuccess {
                if (it.e2eiStatus == MLSClientE2EIStatus.REVOKED) {
                    logger.i("Setting that should notify certificate revocation")
                    userConfigRepository.setShouldNotifyForRevokedCertificate(true)
                }
            }
        }
    }
}
