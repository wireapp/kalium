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

package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.model.PushTokenBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RegisterTokenJson {
    val registerTokenResponse = """ 
            {
             "app":"8218398",
             "client":"123456",
             "token":"oaisjdoiasjd",
             "transport":"GCM"
                }
            """.trimIndent()

    private val jsonProvider = { serializable: PushTokenBody ->
        buildJsonObject {
            put("app", serializable.senderId)
            put("client", serializable.client)
            put("token", serializable.token)
            put("transport", serializable.transport)
        }.toString()
    }

    val validPushTokenRequest =
        ValidJsonProvider(
            PushTokenBody(
                "8218398",
                "123456",
                "oaisjdoiasjd",
                "GCM"
            ), jsonProvider
        )
}
