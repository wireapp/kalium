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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.MLSConversationsVerificationStatusesHandler
import com.wire.kalium.logic.functional.map

/**
 * Use case to check if the CRL is expired and if so, register the external certificates
 */
internal interface CheckRevocationListUseCase {
    suspend operator fun invoke()
}

internal class CheckRevocationListUseCaseImpl(
    private val selfUserId: UserId,
    private val e2EIRepository: E2EIRepository,
    private val mlsClient: MLSClient,
    private val userConfigRepository: UserConfigRepository,
    private val mLSConversationsVerificationStatusesHandler: MLSConversationsVerificationStatusesHandler
) : CheckRevocationListUseCase {
    override suspend fun invoke() {
        e2EIRepository.getCurrentClientDomainCRL().map {
            mlsClient.registerExternalCertificates(it).run {
                userConfigRepository.setCRLExpirationTime(selfUserId.domain, expirationTimestamp)
                if (isThereAnyChanges) {
                    mLSConversationsVerificationStatusesHandler()
                }
            }
        }
    }
}
