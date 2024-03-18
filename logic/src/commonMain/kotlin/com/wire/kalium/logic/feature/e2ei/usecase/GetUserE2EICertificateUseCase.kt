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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.CertificateStatus
import com.wire.kalium.logic.feature.e2ei.CertificateStatusMapper
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map

/**
 * This use case is used to get the e2ei certificate status of specific user
 */
interface GetUserE2eiCertificateStatusUseCase {
    suspend operator fun invoke(userId: UserId): GetUserE2eiCertificateStatusResult
}

class GetUserE2eiCertificateStatusUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository,
    private val certificateStatusMapper: CertificateStatusMapper,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
    private val clientRepository: ClientRepository
) : GetUserE2eiCertificateStatusUseCase {
    override suspend operator fun invoke(userId: UserId): GetUserE2eiCertificateStatusResult =
        if (isE2EIEnabledUseCase()) {
            mlsConversationRepository.getUserIdentity(userId)
                .flatMap { identities ->
                    clientRepository.getClientsByUserId(userId).map { clients ->
                        val clientIds = clients.filter { it.isValid }.map { it.id }
                        identities.getUserCertificateStatus(certificateStatusMapper, clientIds)?.let {
                            GetUserE2eiCertificateStatusResult.Success(it)
                        } ?: GetUserE2eiCertificateStatusResult.Failure.NotActivated
                    }
                }
                .getOrElse { GetUserE2eiCertificateStatusResult.Failure.NotActivated }
        } else {
            GetUserE2eiCertificateStatusResult.Failure.NotActivated
        }
}

sealed class GetUserE2eiCertificateStatusResult {
    class Success(val status: CertificateStatus) : GetUserE2eiCertificateStatusResult()
    sealed class Failure : GetUserE2eiCertificateStatusResult() {
        data object NotActivated : Failure()
    }
}
