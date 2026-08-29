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

package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.util.InternalKaliumApi

/** Local persistence operation caused by an incoming availability message. */
@InternalKaliumApi
public fun interface IncomingAvailabilityPersistence {
    public suspend fun updateAvailabilityStatus(senderUserId: UserId, status: UserAvailabilityStatus)
}

/** DAO-backed incoming-availability persistence shared by continuous and bounded event processing. */
@InternalKaliumApi
public class IncomingAvailabilityPersistenceImpl public constructor(
    private val userDAO: UserDAO,
    private val availabilityStatusMapper: AvailabilityStatusMapper = AvailabilityStatusMapperImpl(),
) : IncomingAvailabilityPersistence {
    override suspend fun updateAvailabilityStatus(senderUserId: UserId, status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(
            senderUserId.toDao(),
            availabilityStatusMapper.fromModelAvailabilityStatusToDao(status),
        )
    }
}
