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

import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.model.UserProfileDTO
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ListUsersResponseJson {

    private val USER_1 = UserId("user10d0-000b-9c1a-000d-a4130002c221", "domain1.example.com")
    private val USER_2 = UserId("user20d0-000b-9c1a-000d-a4130002c221", "domain2.example.com")
    private val USER_3 = UserId("user30d0-000b-9c1a-000d-a4130002c220", "domain3.example.com")

    private val expectedUsersResponse = listOf(
        UserProfileDTO(
            id = USER_1,
            name = "usera",
            handle = "user_a",
            accentId = 2147483647,
            legalHoldStatus = LegalHoldStatusDTO.ENABLED,
            teamId = null,
            assets = emptyList(),
            deleted = false,
            email = null,
            expiresAt = null,
            nonQualifiedId = USER_1.value,
            service = null,
            supportedProtocols = listOf(SupportedProtocolDTO.PROTEUS, SupportedProtocolDTO.MLS)
        ),
        UserProfileDTO(
            id = USER_2,
            name = "userb",
            handle = "user_b",
            accentId = 2147483647,
            legalHoldStatus = LegalHoldStatusDTO.ENABLED,
            teamId = null,
            assets = emptyList(),
            deleted = false,
            email = null,
            expiresAt = null,
            nonQualifiedId = USER_2.value,
            service = null,
            supportedProtocols = listOf(SupportedProtocolDTO.PROTEUS)
        ),
    )

    private val validUserInfoProviderV0 = { userInfo: UserProfileDTO ->
        """
        |{
        | "accent_id": ${userInfo.accentId},
        | "handle": "${userInfo.handle}",
        | "legalhold_status": "enabled",
        | "name": "${userInfo.name}",
        | "assets": ${userInfo.assets},
        | "id": "${userInfo.id.value}",
        | "deleted": "false",
        | "qualified_id": {
        |   "domain": "${userInfo.id.domain}",
        |   "id": "${userInfo.id.value}"
        | }
        |}
        """.trimMargin()
    }

    private val validUserInfoProviderV4 = { userInfo: UserProfileDTO ->
        """
        |{
        | "accent_id": ${userInfo.accentId},
        | "handle": "${userInfo.handle}",
        | "legalhold_status": "enabled",
        | "name": "${userInfo.name}",
        | "assets": ${userInfo.assets},
        | "id": "${userInfo.id.value}",
        | "deleted": "false",
        | "supported_protocols": ${Json.encodeToString(userInfo.supportedProtocols)},
        | "qualified_id": {
        |   "domain": "${userInfo.id.domain}",
        |   "id": "${userInfo.id.value}"
        | }
        |}
        """.trimMargin()
    }

    private val listProvider = { list: List<String> ->
        """
        |[
        | ${list[0]},
        | ${list[1]}
        |]
        """.trimMargin()

    }

    val v0 = ValidJsonProvider(
        expectedUsersResponse.map {
            it.copy(supportedProtocols = null) // we don't expect supported_protocols in v0
        }
    ) {
        listProvider(it.map(validUserInfoProviderV0))
    }

    val v4_withFailedUsers = ValidJsonProvider(
        ListUsersDTO(listOf(USER_3), expectedUsersResponse)
    ) {
        """
        |{
        |    "failed": [
        |      {
        |        "domain": "${it.usersFailed[0].domain}",
        |        "id": "${it.usersFailed[0].value}"
        |      }
        |    ],
        |    "found": ${ listProvider(it.usersFound.map(validUserInfoProviderV4)) }
        |}
        """.trimMargin()
    }

    val v4 = ValidJsonProvider(
        ListUsersDTO(listOf(USER_3), expectedUsersResponse)
    ) {
        """
        |{
        |    "found": ${ listProvider(it.usersFound.map(validUserInfoProviderV4)) }
        |}
        """.trimMargin()
    }
}
