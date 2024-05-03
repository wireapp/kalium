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
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.CertificateStatusMapper
import com.wire.kalium.logic.feature.e2ei.E2eiCertificate
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map

/**
 * This use case is used to get all e2ei certificates of the user.
 * Returns Map<String, E2eiCertificate> where key is value of [ClientId] and [E2eiCertificate] is certificate itself
 */
interface GetUserE2eiCertificatesUseCase {
    suspend operator fun invoke(userId: UserId): Map<String, E2eiCertificate>
}

class GetUserE2eiCertificatesUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
    private val certificateStatusMapper: CertificateStatusMapper
) : GetUserE2eiCertificatesUseCase {
    override suspend operator fun invoke(userId: UserId): Map<String, E2eiCertificate> =
        if (isE2EIEnabledUseCase()) {
            mlsConversationRepository.getUserIdentity(userId).map { identities ->
                val result = mutableMapOf<String, E2eiCertificate>()
                identities.forEach {
                    E2eiCertificate.fromWireIdentity(it)?.let { certificate ->
                        result[it.clientId.value] = certificate
                    }
                }
                result
            }.getOrElse(mapOf())
        } else mapOf()
}
