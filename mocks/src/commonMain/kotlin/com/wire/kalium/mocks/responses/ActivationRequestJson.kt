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

import com.wire.kalium.network.api.base.unauthenticated.register.ActivationParam
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ActivationRequestJson {

    private val jsonProvider = { serializable: ActivationParam ->
        buildJsonObject {
            put("dryrun", serializable.dryRun)
            when (serializable) {
                is ActivationParam.Email -> {
                    put("email", serializable.email)
                    put("code", serializable.code)
                }
            }
        }.toString()
    }

    val validActivateEmail = ValidJsonProvider(
        ActivationParam.Email(email = "user@domain.de", code = "123456"),
        jsonProvider
    )
}
