/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRedirect
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRegistrationDTO
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DomainRegistrationResponseJson {

    val success = ValidJsonProvider(
        serializableData = DomainRegistrationDTO(
            backendUrl = null,
            domainRedirect = DomainRedirect.NO_REGISTRATION,
            ssoCode = null
        ),
        jsonProvider = { serializable ->
            """
            {
                "backend_url": "${serializable.backendUrl}",
                "domain_redirect": "${serializable.domainRedirect}",
                "sso_code": "${serializable.ssoCode}"
            }
            """.trimIndent()
        }
    )

    val invalidDomain = ValidJsonProvider(
        serializableData = ErrorResponse(
            code = 400,
            label = "invalid-domain",
            message = "invalid-domain"
        ),
        jsonProvider = { serializable ->
            buildJsonObject {
                put("code", serializable.code)
                put("label", serializable.label)
                put("message", serializable.message)
            }.toString()
        }
    )

}
