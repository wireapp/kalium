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

import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.model.UserProfileDTO

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
            supportedProtocols = null
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
            supportedProtocols = null
        ),
    )

    private val validUserInfoProvider = { userInfo: List<UserProfileDTO> ->
        """
        |[
        |   {
        |    "accent_id": ${userInfo[0].accentId},
        |    "handle": "${userInfo[0].handle}",
        |    "legalhold_status": "enabled",
        |    "name": "${userInfo[0].name}",
        |    "assets": ${userInfo[0].assets},
        |    "id": "${userInfo[0].id.value}",
        |    "deleted": "false",
        |    "qualified_id": {
        |      "domain": "${userInfo[0].id.domain}",
        |      "id": "${userInfo[0].id.value}"
        |    }
        |   },
        |   {
        |    "accent_id": ${userInfo[1].accentId},
        |    "handle": "${userInfo[1].handle}",
        |    "legalhold_status": "enabled",
        |    "name": "${userInfo[1].name}",
        |    "assets": ${userInfo[1].assets},
        |    "id": "${userInfo[1].id.value}",
        |    "deleted": "false",
        |    "qualified_id": {
        |      "domain": "${userInfo[1].id.domain}",
        |      "id": "${userInfo[1].id.value}"
        |    }
        |   }
        |]
        """.trimMargin()
    }

    val v0 = ValidJsonProvider(
        expectedUsersResponse
    ) {
        validUserInfoProvider(it)
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
        |    "found": ${validUserInfoProvider(it.usersFound)}
        |}
        """.trimMargin()
    }

    val v4 = ValidJsonProvider(
        ListUsersDTO(listOf(USER_3), expectedUsersResponse)
    ) {
        """
        |{
        |    "found": ${validUserInfoProvider(it.usersFound)}
        |}
        """.trimMargin()
    }
}
