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

import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import kotlinx.serialization.json.buildJsonObject

object LoginWithEmailRequestJson {
    private val jsonProvider = { serializable: LoginApi.LoginParam ->
        buildJsonObject {
            "password" to serializable.password
            "label" to serializable.label
            when (serializable) {
                is LoginApi.LoginParam.LoginWithEmail -> "email" to serializable.email
                is LoginApi.LoginParam.LoginWithHandle -> "handle" to serializable.handle
            }
        }.toString()
    }

    val validLoginWithEmail = ValidJsonProvider(
        LoginApi.LoginParam.LoginWithEmail(
            email = "user@email.de",
            label = "label",
            password = "password",
            verificationCode = "verificationCode"
        ), jsonProvider
    )

    val validLoginWithHandle = ValidJsonProvider(
        LoginApi.LoginParam.LoginWithHandle(
            handle = "cool_user_name",
            label = "label",
            password = "password",
        ), jsonProvider
    )

    val missingEmailAndHandel = FaultyJsonProvider(
        """
        |{
        |  "label": "label",
        |  "password": "password",
        |}
        """.trimMargin()
    )

    val missingLabel = FaultyJsonProvider(
        """
        |{
        |  "email": "user@email.de",
        |  "password": "password",
        |}
        """.trimMargin()
    )

    val missingPassword = FaultyJsonProvider(
        """
        |{
        |  "label": "label",
        |  "email": "user@email.de",
        |}
        """.trimMargin()
    )
}
