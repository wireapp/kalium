package com.wire.kalium.api.tools.json.api.teams

import com.wire.kalium.api.tools.json.AnyResponseProvider
import com.wire.kalium.network.api.teams.TeamsApi

object TeamsResponsesJson {

    object GetTeams {
        private val jsonProvider = { option: TeamsApi.GetTeamsOption ->
            when (option) {
                is TeamsApi.GetTeamsOption.LimitTo ->
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
                is TeamsApi.GetTeamsOption.StartFrom ->
                    """
                |{
                |   "has_more": false,
                |   "teams": []
                |}
                """.trimMargin()
            }
        }

        val validGetTeamsLimitTo = { listOfTeamIds: List<String> ->
            AnyResponseProvider(data = TeamsApi.GetTeamsOption.LimitTo(listOfTeamIds), jsonProvider)
        }

        val validGetTeamsStartFrom = { startFromTeamId: String ->
            AnyResponseProvider(data = TeamsApi.GetTeamsOption.StartFrom(startFromTeamId), jsonProvider)
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

