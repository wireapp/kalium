/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides the self user's team id. The value is stable for the session, so it is read from storage
 * once and reused afterwards instead of being fetched on every call.
 */
internal interface SelfTeamIdProvider {
    suspend operator fun invoke(): String?
}

internal class SelfTeamIdProviderImpl(
    private val selfUserId: UserId,
    private val usersRepository: CellUsersRepository,
) : SelfTeamIdProvider {

    private val mutex = Mutex()
    private var cachedTeamId: String? = null
    private var loaded = false

    override suspend fun invoke(): String? = mutex.withLock {
        if (!loaded) {
            cachedTeamId = usersRepository.getUserTeamId(selfUserId).getOrElse(null)
            loaded = true
        }
        cachedTeamId
    }
}
