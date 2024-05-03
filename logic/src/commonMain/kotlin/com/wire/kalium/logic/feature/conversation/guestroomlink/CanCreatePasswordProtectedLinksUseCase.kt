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
package com.wire.kalium.logic.feature.conversation.guestroomlink

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

/**
 * Use case to check if the current user can create password protected invite links.
 * This is only possible if the server api version is greater or equal to 4.
 */
class CanCreatePasswordProtectedLinksUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val selfUserId: UserId
) {
    suspend operator fun invoke(): Boolean =
        serverConfigRepository.configForUser(selfUserId).fold(
            { false },
            {
                it.metaData.commonApiVersion.version >= MIN_API_TO_SUPPORT_PASSWORD_LINKS
            }
        )

    private companion object {
        const val MIN_API_TO_SUPPORT_PASSWORD_LINKS = 4
    }
}
