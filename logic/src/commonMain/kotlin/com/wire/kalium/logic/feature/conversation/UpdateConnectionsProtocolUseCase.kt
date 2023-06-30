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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import kotlinx.coroutines.flow.first

interface UpdateConnectionsProtocolUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

class UpdateConnectionsProtocolUseCaseImpl(
    private val connectionRepository: ConnectionRepository
): UpdateConnectionsProtocolUseCase {
    override suspend fun invoke(): Either<CoreFailure, Unit> {
        val connections = connectionRepository.observeConnectionList().first()
        return connections.foldToEitherWhileRight(Unit) { item, accumulated ->
            connectionRepository.updateProtocolForConnection(item)
        }
    }
}
