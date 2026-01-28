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

import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse

object SimpleClientResponseJson {
    private val missingClassJsonProvider = { serializable: SimpleClientResponse ->
        """
        |{
        |   "id": "${serializable.id}"
        |}
        """.trimMargin()
    }
    private val gibberishClassJsonProvider = { serializable: SimpleClientResponse ->
        """
        |{
        |   "id": "${serializable.id}",
        |   "class": "198237juf9"
        |}
        """.trimMargin()
    }
    private val jsonProvider = { serializable: SimpleClientResponse ->
        """
        |{
        |   "id": "${serializable.id}",
        |   "class": "${serializable.deviceClass}"
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        SimpleClientResponse(
            id = "3b3a54c770f5e1a4",
            deviceClass = DeviceTypeDTO.Phone
        ),
        jsonProvider
    )

    val validMissingClass = ValidJsonProvider(
        SimpleClientResponse(
            id = "3b3a54c770f5e1a4"
        ),
        missingClassJsonProvider
    )

    val validGibberishClass = ValidJsonProvider(
        SimpleClientResponse(
            id = "3b3a54c770f5e1a4"
        ),
        gibberishClassJsonProvider
    )

    fun createValid(clientResponse: SimpleClientResponse) = ValidJsonProvider(clientResponse, jsonProvider)
}
