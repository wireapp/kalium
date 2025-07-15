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
import com.wire.kalium.logic.feature.e2ei.MLSClientIdentity
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase

/**
 * This use case is used to get all MLSClientIdentities of the user.
 * Returns Map<String, MLSClientIdentity> where key is value of [ClientId] and [MLSClientIdentity] is certificate itself
 */
interface GetUserMlsClientIdentitiesUseCase {
    suspend operator fun invoke(userId: UserId): Map<String, MLSClientIdentity>
}

class GetUserMlsClientIdentitiesUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository,
    private val isMlsEnabledUseCase: IsMLSEnabledUseCase,
) : GetUserMlsClientIdentitiesUseCase {
    override suspend operator fun invoke(userId: UserId): Map<String, MLSClientIdentity> =
        if (isMlsEnabledUseCase()) {
            mlsConversationRepository.getUserIdentity(userId).map { identities ->
                val result = mutableMapOf<String, MLSClientIdentity>()
                identities.forEach {
                        result[it.clientId.value] = MLSClientIdentity.fromWireIdentity(it)
                }
                result
            }.getOrElse(mapOf())
        } else mapOf()
}
