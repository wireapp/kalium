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

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.e2ei.E2eiCertificateRepository
import com.wire.kalium.logic.feature.e2ei.E2eiCertificate
import com.wire.kalium.logic.feature.e2ei.PemCertificateDecoder
import com.wire.kalium.logic.functional.fold

/**
 * This use case is used to get the e2ei certificate
 */
interface GetE2eiCertificateUseCase {
    operator fun invoke(clientId: ClientId): GetE2EICertificateUseCaseResult
}

class GetE2eiCertificateUseCaseImpl(
    private val e2eiCertificateRepository: E2eiCertificateRepository,
    private val pemCertificateDecoder: PemCertificateDecoder
) : GetE2eiCertificateUseCase {
    override operator fun invoke(clientId: ClientId): GetE2EICertificateUseCaseResult =
        e2eiCertificateRepository.getE2eiCertificate(clientId).fold(
            {
                GetE2EICertificateUseCaseResult.Failure.NotActivated
            },
            {
                val certificate = pemCertificateDecoder.decode(it)
                GetE2EICertificateUseCaseResult.Success(certificate)
            }
        )
}

sealed class GetE2EICertificateUseCaseResult {
    class Success(val certificate: E2eiCertificate) : GetE2EICertificateUseCaseResult()
    sealed class Failure : GetE2EICertificateUseCaseResult() {
        data object NotActivated : Failure()
    }
}
