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

import com.wire.kalium.network.api.authenticated.CreateUserTeamDTO
import com.wire.kalium.network.api.model.ErrorResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MigrationUserToTeamResponseJson {
    val success = ValidJsonProvider(
        serializableData = CreateUserTeamDTO(
            teamId = "teamId",
            teamName = "teamName"
        ),
        jsonProvider = { serializable ->
            buildJsonObject {
                put("team_id", serializable.teamId)
                put("team_name", serializable.teamName)
            }.toString()
        }
    )

    val failedUserInTeam = ValidJsonProvider(
        serializableData = ErrorResponse(
            code = 403,
            label = "user-already-in-a-team",
            message = "Switching teams is not allowed"
        ),
        jsonProvider = { serializable ->
            buildJsonObject {
                put("code", serializable.code)
                put("label", serializable.label)
                put("message", serializable.message)
            }.toString()
        }
    )

    val failedUserNotFound = ValidJsonProvider(
        serializableData = ErrorResponse(
            code = 404,
            label = "not-found",
            message = "User not found"
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