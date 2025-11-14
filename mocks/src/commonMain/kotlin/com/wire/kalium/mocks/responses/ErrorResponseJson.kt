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

import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.api.model.FederationErrorResponse

object ErrorResponseJson {
    private val jsonProvider = { serializable: GenericAPIErrorResponse ->
        """
        |{
        |  "code": ${serializable.code},
        |  "label": "${serializable.label}",
        |  "message": "${serializable.message}"
        |}
        """.trimMargin()
    }

    private val federationConflictJsonProvider = { serializable: FederationErrorResponse.Conflict ->
        """
        |{
        |  "non_federating_backends": ${serializable.nonFederatingBackends}
        |}
        """.trimMargin()
    }

    private val federationUnreachableJsonProvider = { serializable: FederationErrorResponse.Unreachable ->
        """
        |{
        |  "unreachable_backends": ${serializable.unreachableBackends}
        |}
        """.trimMargin()
    }

    private val genericFederationJsonProvider = { serializable: FederationErrorResponse.Generic ->
        """
        |{
        |   "label":"${serializable.label}",
        |   "code": ${serializable.code},
        |   "message": "${serializable.message}",
        |   "data": {
        |       "type": "${serializable.cause!!.type}",
        |       "domains": ${serializable.cause!!.domains},
        |       "path": "${serializable.cause!!.path}"
        |   }
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        GenericAPIErrorResponse(code = 499, label = "error_label", message = "error_message"),
        jsonProvider
    )

    fun valid(error: GenericAPIErrorResponse) = ValidJsonProvider(
        error,
        jsonProvider
    )

    fun validFederationConflictingBackends(error: FederationErrorResponse.Conflict) = ValidJsonProvider(
        error,
        federationConflictJsonProvider
    )

    fun validFederationUnreachableBackends(error: FederationErrorResponse.Unreachable) = ValidJsonProvider(
        error,
        federationUnreachableJsonProvider
    )

    fun validFederationGeneric(error: FederationErrorResponse.Generic) = ValidJsonProvider(
        error,
        genericFederationJsonProvider
    )
}
