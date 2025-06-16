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

package com.wire.kalium.logic.configuration.notification

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.client.TokenStorage
import io.mockative.Mockable

data class NotificationToken(val token: String, val transport: String, val applicationId: String)

@Mockable
interface NotificationTokenRepository {

    fun persistNotificationToken(token: String, transport: String, applicationId: String): Either<StorageFailure, Unit>
    fun getNotificationToken(): Either<StorageFailure, NotificationToken>
}

class NotificationTokenDataSource(
    private val tokenStorage: TokenStorage
) : NotificationTokenRepository {

    override fun persistNotificationToken(token: String, transport: String, applicationId: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { tokenStorage.saveToken(token, transport, applicationId) }

    override fun getNotificationToken(): Either<StorageFailure, NotificationToken> = wrapStorageRequest { tokenStorage.getToken() }.map {
        with(it) { NotificationToken(token, transport, applicationId) }
    }
}
