package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.tools.json.AnyResponseProvider

object ConversationDetailsResponse {

    private val jsonProvider = { _: String ->
        """
        |{
        |    "failed": [],
        |    "found": [
        |        {
        |            "access": [
        |                "invite"
        |            ],
        |            "access_role": "activated",
        |            "access_role_v2": [
        |                "team_member",
        |                "non_team_member",
        |                "service"
        |            ],
        |            "creator": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |            "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b",
        |            "last_event": "0.0",
        |            "last_event_time": "1970-01-01T00:00:00.000Z",
        |            "members": {
        |                "others": [
        |                    {
        |                        "conversation_role": "wire_member",
        |                        "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613",
        |                        "qualified_id": {
        |                            "domain": "bella.wire.link",
        |                            "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613"
        |                        },
        |                        "status": 0
        |                    }
        |                ],
        |                "self": {
        |                    "conversation_role": "wire_admin",
        |                    "hidden": false,
        |                    "hidden_ref": null,
        |                    "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |                    "otr_archived": false,
        |                    "otr_archived_ref": null,
        |                    "otr_muted_ref": null,
        |                    "otr_muted_status": null,
        |                    "qualified_id": {
        |                        "domain": "anta.wire.link",
        |                        "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b"
        |                    },
        |                    "service": null,
        |                    "status": 0,
        |                    "status_ref": "0.0",
        |                    "status_time": "1970-01-01T00:00:00.000Z"
        |                }
        |            },
        |            "message_timer": null,
        |            "name": "test-anta-grp",
        |            "protocol": "proteus",
        |            "qualified_id": {
        |                "domain": "anta.wire.link",
        |                "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b"
        |            },
        |            "receipt_mode": 0,
        |            "team": null,
        |            "type": 0
        |        },
        |        {
        |            "access": [
        |                "private"
        |            ],
        |            "access_role": "private",
        |            "access_role_v2": [],
        |            "creator": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |            "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |            "last_event": "0.0",
        |            "last_event_time": "1970-01-01T00:00:00.000Z",
        |            "members": {
        |                "others": [],
        |                "self": {
        |                    "conversation_role": "wire_admin",
        |                    "hidden": false,
        |                    "hidden_ref": null,
        |                    "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |                    "otr_archived": false,
        |                    "otr_archived_ref": null,
        |                    "otr_muted_ref": null,
        |                    "otr_muted_status": null,
        |                    "qualified_id": {
        |                        "domain": "anta.wire.link",
        |                        "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b"
        |                    },
        |                    "service": null,
        |                    "status": 0,
        |                    "status_ref": "0.0",
        |                    "status_time": "1970-01-01T00:00:00.000Z"
        |                }
        |            },
        |            "message_timer": null,
        |            "name": null,
        |            "protocol": "proteus",
        |            "qualified_id": {
        |                "domain": "anta.wire.link",
        |                "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b"
        |            },
        |            "receipt_mode": null,
        |            "team": null,
        |            "type": 1
        |        }
        |    ],
        |    "not_found": []
        |}
        """.trimIndent()
    }

    val validGetDetailsForIds = AnyResponseProvider(data = "", jsonProvider)
}

