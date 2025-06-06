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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.feature.e2ei.MLSClientIdentity
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import io.mockative.Mockable

/**
 * This use case is used to get the e2ei certificate
 */
@Mockable
interface GetMLSClientIdentityUseCase {
    suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, MLSClientIdentity>
}

class GetMLSClientIdentityUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository
) : GetMLSClientIdentityUseCase {
    override suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, MLSClientIdentity> =
        mlsConversationRepository.getClientIdentity(clientId).flatMap {
                it?.let {
                    MLSClientIdentity.fromWireIdentity(it).right()
                } ?: StorageFailure.DataNotFound.left()
            }
}
