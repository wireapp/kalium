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

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.functional.map

internal interface CheckRevocationListUseCase {
    suspend operator fun invoke(domain: String = "fakeDomain")
}

internal class CheckRevocationListUseCaseImpl(
    private val e2EIRepository: E2EIRepository,
    private val mlsClient: MLSClient,
    private val userConfigRepository: UserConfigRepository
) : CheckRevocationListUseCase {
    override suspend fun invoke(domain: String) {
        e2EIRepository.getCRL().map {
            mlsClient.registerExternalCertificates(it).run {
                userConfigRepository.setCRLExpirationTime(domain, expirationTimestamp)
                if (isThereAnyChanges) {
                    // Do we need to update conversations manullay ? we are observing these cons  using MLSConversationsVerificationStatusesHandler
                }
            }
        }
    }
}
