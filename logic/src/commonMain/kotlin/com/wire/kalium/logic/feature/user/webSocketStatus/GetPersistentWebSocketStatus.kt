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

package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold

interface GetPersistentWebSocketStatus {
    suspend operator fun invoke(): Boolean
}

internal class GetPersistentWebSocketStatusImpl(
    private val userId: UserId,
    private val sessionRepository: SessionRepository
) : GetPersistentWebSocketStatus {
    override suspend operator fun invoke(): Boolean =
        sessionRepository.persistentWebSocketStatus(userId).fold({
            false
        }, {
            it
        })
}
