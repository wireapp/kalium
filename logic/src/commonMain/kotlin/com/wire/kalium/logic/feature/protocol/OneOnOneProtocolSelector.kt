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
package com.wire.kalium.logic.feature.protocol

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap

internal interface OneOnOneProtocolSelector {
    suspend fun getProtocolForUser(userId: UserId): Either<CoreFailure, SupportedProtocol>
}

internal class OneOnOneProtocolSelectorImpl(
    private val userRepository: UserRepository
) : OneOnOneProtocolSelector {
    override suspend fun getProtocolForUser(userId: UserId): Either<CoreFailure, SupportedProtocol> =
        userRepository.userById(userId).flatMap { otherUser ->
            val selfUser = userRepository.getSelfUser() ?: run {
                val error = NullPointerException("Self user unobtainable when selecting protocol for user")
                return@flatMap Either.Left(CoreFailure.Unknown(error))
            }

            val selfUserProtocols = selfUser.supportedProtocols.orEmpty()
            val otherUserProtocols = otherUser.supportedProtocols.orEmpty()

            val commonProtocols = selfUserProtocols.intersect(otherUserProtocols)

            return when {
                commonProtocols.contains(SupportedProtocol.MLS) -> Either.Right(SupportedProtocol.MLS)
                commonProtocols.contains(SupportedProtocol.PROTEUS) -> Either.Right(SupportedProtocol.PROTEUS)
                else -> Either.Left(CoreFailure.NoCommonProtocolFound)
            }
        }
}
