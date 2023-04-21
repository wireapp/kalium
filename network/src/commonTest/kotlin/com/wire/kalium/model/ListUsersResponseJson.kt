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
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserProfileDTO
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.UserId

object ListUsersResponseJson {

    private val USER_1 = UserId("user10d0-000b-9c1a-000d-a4130002c221", "domain1.example.com")
    private val USER_2 = UserId("user20d0-000b-9c1a-000d-a4130002c221", "domain2.example.com")

    private val expectedUsersResponse = listOf(
        UserProfileDTO(
            id = USER_1,
            name = "usera",
            handle = "user_a",
            accentId = 2147483647,
            legalHoldStatus = LegalHoldStatusResponse.ENABLED,
            teamId = null,
            assets = emptyList(),
            deleted = false,
            email = null,
            expiresAt = null,
            nonQualifiedId = USER_1.value,
            service = null
        ),
        UserProfileDTO(
            id = USER_2,
            name = "userb",
            handle = "user_b",
            accentId = 2147483647,
            legalHoldStatus = LegalHoldStatusResponse.ENABLED,
            teamId = null,
            assets = emptyList(),
            deleted = false,
            email = null,
            expiresAt = null,
            nonQualifiedId = USER_2.value,
            service = null
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

    val valid = ValidJsonProvider(
        expectedUsersResponse
    ) {
        validUserInfoProvider(it)
    }

    val valid4 = ValidJsonProvider(
        ListUsersDTO(emptyList(), expectedUsersResponse)
    ) {
        """
        |{
        |    "failed": [
        |      {
        |        "domain": "domain3.example.com",
        |        "id": "99db9768-04e3-4b5d-9268-831b6a25c4ab"
        |      }
        |    ],
        |    "found": ${validUserInfoProvider(it.usersFound)}
        |}
        """.trimMargin()
    }
}
