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

package com.wire.kalium.mocks.responses

import com.wire.kalium.network.api.unauthenticated.register.RegisterParam
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RegisterAccountJson {
    private val jsonProvider = { serializable: RegisterParam.PersonalAccount ->
        buildJsonObject {
            put("name", serializable.name)
            put("password", serializable.password)
            put("email", serializable.email)
            put("email_code", serializable.emailCode)
            serializable.cookieLabel?.let { put("label", it) }
        }.toString()
    }

    val validPersonalAccountRegister = ValidJsonProvider(
        serializableData = RegisterParam.PersonalAccount(
            "test@email.com",
            "123456",
            "private user",
            "password",
            "cookieLabel"
        ),
        jsonProvider = jsonProvider
    )
}
