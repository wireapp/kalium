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

package com.wire.kalium.logic.data.id

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mockable

@Mockable
interface FederatedIdMapper {
    suspend fun parseToFederatedId(qualifiedID: QualifiedID): String
    suspend fun parseToFederatedId(qualifiedStringID: String): String
}

/**
 * Mapper that enables parsing [QualifiedID] into a [String] having in consideration federation.
 *
 * In detail, if [isFederationEnabled] is [true] then the full qualified form will be used
 * otherwise the plain value will be used
 */
class FederatedIdMapperImpl internal constructor(
    private val selfUserId: UserId,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val sessionRepository: SessionRepository,
) : FederatedIdMapper {

    private suspend fun isFederationEnabled() = sessionRepository.isFederated(selfUserId).fold(
        { false },
        { it }
    )

    override suspend fun parseToFederatedId(qualifiedID: QualifiedID): String {
        kaliumLogger.v(
            "Parsing stringId: ${qualifiedID.value.obfuscateId()}@${qualifiedID.domain.obfuscateDomain()} " +
                    "| FederationEnabled? ${isFederationEnabled()}"
        )
        return if (isFederationEnabled() && qualifiedID.domain.isNotEmpty()) {
            qualifiedID.toString()
        } else {
            qualifiedID.value
        }
    }

    override suspend fun parseToFederatedId(qualifiedStringID: String): String {
        val parsedQualifiedID = qualifiedIdMapper.fromStringToQualifiedID(qualifiedStringID)
        kaliumLogger.v(
            "Parsing stringId: ${parsedQualifiedID.value.obfuscateId()}" +
                    "@${parsedQualifiedID.domain.obfuscateDomain()} |" +
                    " FederationEnabled? ${isFederationEnabled()}"
        )
        return if (isFederationEnabled() && parsedQualifiedID.domain.isNotEmpty()) {
            parsedQualifiedID.toString()
        } else {
            parsedQualifiedID.value
        }
    }
}
