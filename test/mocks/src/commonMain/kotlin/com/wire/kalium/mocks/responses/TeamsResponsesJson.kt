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

import com.wire.kalium.network.api.authenticated.teams.GetTeamsOption

object TeamsResponsesJson {

    object GetTeams {
        private val jsonProvider = { option: GetTeamsOption ->
            when (option) {
                is GetTeamsOption.LimitTo ->
                    """
                |{
                |  "has_more": false,
                |  "teams": [
                |       {
                |           "creator": "33f1df6b-0bed-4a72-bd09-61b871178b9d",
                |           "icon": "default",
                |           "name": "Gonzo",
                |           "id": "770b0623-ffd5-4e08-8092-7a6b9b9ca3b4",
                |           "binding": true
                |       }
                |  ]
                |}
                """.trimMargin()

                is GetTeamsOption.StartFrom ->
                    """
                |{
                |   "has_more": false,
                |   "teams": []
                |}
                """.trimMargin()
            }
        }

        val validGetTeamsLimitTo = { listOfTeamIds: List<String> ->
            AnyResponseProvider(data = GetTeamsOption.LimitTo(listOfTeamIds), jsonProvider)
        }

        val validGetTeamsStartFrom = { startFromTeamId: String ->
            AnyResponseProvider(data = GetTeamsOption.StartFrom(startFromTeamId), jsonProvider)
        }
    }

    object GetTeamsMembers {
        private val jsonProvider = { _: String ->
            """
        |{
        |   "members": [
        |       {
        |           "user": "33f1df6b-0bed-4a72-bd09-61b871178b9d",
        |           "created_by": null,
        |           "legalhold_status": "no_consent",
        |           "created_at": null,
        |           "permissions": {
        |               "copy": 8191,
        |               "self": 8191
        |           }
        |       },
        |       {
        |           "user": "57b0f4d8-657f-4730-a8fa-a8b1456f6b4c",
        |           "created_by": "33f1df6b-0bed-4a72-bd09-61b871178b9d",
        |           "legalhold_status": "no_consent",
        |           "created_at": "2021-12-09T14:11:09.246Z",
        |           "permissions": {
        |               "copy": 1587,
        |               "self": 1587
        |           }
        |       }
        |   ],
        |   "hasMore": false
        |}
        """.trimMargin()
        }

        val validGetTeamsMembers = AnyResponseProvider(data = "", jsonProvider)
    }
}
