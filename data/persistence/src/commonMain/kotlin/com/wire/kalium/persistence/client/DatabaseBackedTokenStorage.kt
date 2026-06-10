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

package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.daokaliumdb.GlobalPushRegistrationEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalSecretsDAO
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

internal class DatabaseBackedTokenStorage(
    private val globalSecretsDAO: GlobalSecretsDAO,
    private val clock: Clock = Clock.System
) : TokenStorage {
    override fun saveToken(token: String, transport: String, applicationId: String) {
        runBlocking {
            globalSecretsDAO.upsertPushRegistration(
                GlobalPushRegistrationEntity(
                    token = token,
                    transport = transport,
                    applicationId = applicationId,
                    updatedAt = clock.now().toEpochMilliseconds()
                )
            )
        }
    }

    override fun getToken(): NotificationTokenEntity? = runBlocking {
        globalSecretsDAO.pushRegistration()?.let {
            NotificationTokenEntity(
                token = it.token,
                transport = it.transport,
                applicationId = it.applicationId
            )
        }
    }
}
