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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.logger.kaliumLogger

internal interface OneOnOneProtocolSelector {
    suspend fun getProtocolForUser(userId: UserId): Either<CoreFailure, SupportedProtocol>
}

internal class OneOnOneProtocolSelectorImpl(
    private val userRepository: UserRepository,
    private val userConfigRepository: UserConfigRepository
) : OneOnOneProtocolSelector {
    override suspend fun getProtocolForUser(userId: UserId): Either<CoreFailure, SupportedProtocol> =
        userRepository.userById(userId).flatMap { otherUser ->
            val selfUser = userRepository.getSelfUser().getOrElse {
                kaliumLogger.d("Self user unobtainable when selecting protocol for user")
                return@flatMap it.left()
            }

            val teamDefaultProtocol = userConfigRepository.getDefaultProtocol().getOrNull()
            val selfUserProtocols = selfUser.supportedProtocols.orEmpty()
            val otherUserProtocols = otherUser.supportedProtocols.orEmpty()
            val commonProtocols = selfUserProtocols.intersect(otherUserProtocols)

            kaliumLogger.withTextTag(TAG).d(
                "teamDefaultProtocol = $teamDefaultProtocol, " +
                        "selfUserProtocols = $selfUserProtocols, " +
                        "otherUserProtocols = $otherUserProtocols, " +
                        "commonProtocols = $commonProtocols"
            )

            return when {
                commonProtocols.contains(SupportedProtocol.MLS) -> Either.Right(SupportedProtocol.MLS)
                commonProtocols.contains(SupportedProtocol.PROTEUS) -> Either.Right(SupportedProtocol.PROTEUS)
                selfUserProtocols.contains(SupportedProtocol.MLS) -> Either.Left(CoreFailure.NoCommonProtocolFound.OtherNeedToUpdate)
                else -> Either.Left(CoreFailure.NoCommonProtocolFound.SelfNeedToUpdate)
            }
        }

    private companion object {
        const val TAG = "OneOnOneProtocolSelector"
    }
}
