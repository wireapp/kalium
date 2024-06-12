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

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface TokenStorage {
    /**
     * to save the token that generated from the service so it can be used to register this token in the server later
     * ex: firebase token
     * the transport here is the type of the token ("GCM,APNS")
     */
    fun saveToken(token: String, transport: String, applicationId: String)

    /**
     * get the saved token with it's type
     */
    fun getToken(): NotificationTokenEntity?
}

@Serializable
data class NotificationTokenEntity(
    @SerialName("token") val token: String,
    @SerialName("transport") val transport: String,
    @SerialName("applicationId") val applicationId: String,
)

internal class TokenStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences
) : TokenStorage {

    override fun saveToken(token: String, transport: String, applicationId: String) {
        kaliumPreferences.putSerializable(
            NOTIFICATION_TOKEN,
            NotificationTokenEntity(token, transport, applicationId),
            NotificationTokenEntity.serializer()
        )
    }

    override fun getToken(): NotificationTokenEntity? =
        kaliumPreferences.getSerializable(NOTIFICATION_TOKEN, NotificationTokenEntity.serializer())

    private companion object {
        const val NOTIFICATION_TOKEN = "notification_token"
    }

}
