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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.UserConfigDAO
import com.wire.kalium.util.InternalKaliumApi

/** Tracking-identifier persistence required while receiving data-transfer messages. */
@InternalKaliumApi
public interface TrackingIdentifierStorage {
    public suspend fun getCurrentTrackingIdentifier(): String?
    public suspend fun setCurrentTrackingIdentifier(newIdentifier: String)
    public suspend fun setPreviousTrackingIdentifier(identifier: String)
}

/** DAO-backed tracking-identifier storage shared by the app and future bounded receivers. */
@InternalKaliumApi
public class TrackingIdentifierStorageImpl public constructor(
    private val userConfigDAO: UserConfigDAO,
) : TrackingIdentifierStorage {
    override suspend fun getCurrentTrackingIdentifier(): String? =
        userConfigDAO.getTrackingIdentifier()

    override suspend fun setCurrentTrackingIdentifier(newIdentifier: String) {
        wrapStorageRequest {
            userConfigDAO.setTrackingIdentifier(identifier = newIdentifier)
        }
    }

    override suspend fun setPreviousTrackingIdentifier(identifier: String) {
        wrapStorageRequest {
            userConfigDAO.setPreviousTrackingIdentifier(identifier = identifier)
        }
    }
}
