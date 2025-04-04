/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

internal class CellUsersDataSource(
    private val userDAO: UserDAO,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : CellUsersRepository {
    override suspend fun getUserNames() = withContext(dispatchers.io) {
        wrapStorageRequest {
            userDAO.getAllUsersDetails().firstOrNull()?.mapNotNull { user ->
                user.name?.let { name ->
                    user.id.toString() to name
                }
            }
        }
    }
}
