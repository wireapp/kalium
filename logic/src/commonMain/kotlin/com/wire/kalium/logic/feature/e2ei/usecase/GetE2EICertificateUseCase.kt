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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.feature.e2ei.CertificateStatusMapper
import com.wire.kalium.logic.feature.e2ei.E2eiCertificate
import com.wire.kalium.logic.functional.fold

/**
 * This use case is used to get the e2ei certificate
 */
interface GetE2eiCertificateUseCase {
    suspend operator fun invoke(clientId: ClientId): GetE2EICertificateUseCaseResult
}

class GetE2eiCertificateUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository,
    private val certificateStatusMapper: CertificateStatusMapper
) : GetE2eiCertificateUseCase {
    override suspend operator fun invoke(clientId: ClientId): GetE2EICertificateUseCaseResult =
        mlsConversationRepository.getClientIdentity(clientId).fold(
            {
                if (it is StorageFailure.DataNotFound) GetE2EICertificateUseCaseResult.NotActivated
                else GetE2EICertificateUseCaseResult.Failure
            },
            {
                it?.let {
                    val certificate = E2eiCertificate(
                        status = certificateStatusMapper.toCertificateStatus(it.status),
                        serialNumber = it.serialNumber,
                        certificateDetail = it.certificate
                    )
                    GetE2EICertificateUseCaseResult.Success(certificate)
                } ?: GetE2EICertificateUseCaseResult.NotActivated
            }
        )
}

sealed class GetE2EICertificateUseCaseResult {
    class Success(val certificate: E2eiCertificate) : GetE2EICertificateUseCaseResult()
    data object NotActivated : GetE2EICertificateUseCaseResult()
    data object Failure : GetE2EICertificateUseCaseResult()
}
