/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RegisterAccountJson {
    private val jsonProvider = { serializable: RegisterApi.RegisterParam.PersonalAccount ->
        buildJsonObject {
            put("name", serializable.name)
            put("password", serializable.password)
            put("email", serializable.email)
            put("email_code", serializable.emailCode)
        }.toString()
    }

    val validPersonalAccountRegister = ValidJsonProvider(
        serializableData = RegisterApi.RegisterParam.PersonalAccount(
            "test@email.com",
            "123456",
            "private user",
            "password"
        ),
        jsonProvider = jsonProvider
    )
}
