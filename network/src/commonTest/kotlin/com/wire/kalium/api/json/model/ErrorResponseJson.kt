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

package com.wire.kalium.api.json.model

import com.wire.kalium.mocks.responses.ValidJsonProvider
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.FederationConflictResponse

object ErrorResponseJson {
    private val jsonProvider = { serializable: ErrorResponse ->
        """
        |{
        |  "code": ${serializable.code},
        |  "label": "${serializable.label}",
        |  "message": "${serializable.message}"
        |}
        """.trimMargin()
    }

    private val federationConflictJsonProvider = { serializable: FederationConflictResponse ->
        """
        |{
        |  "non_federating_backends": ${serializable.nonFederatingBackends}
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        ErrorResponse(code = 499, label = "error_label", message = "error_message"),
        jsonProvider
    )

    fun valid(error: ErrorResponse) = ValidJsonProvider(
        error,
        jsonProvider
    )

    fun validFederationConflictingBackends(error: FederationConflictResponse) = ValidJsonProvider(
        error,
        federationConflictJsonProvider
    )
}
