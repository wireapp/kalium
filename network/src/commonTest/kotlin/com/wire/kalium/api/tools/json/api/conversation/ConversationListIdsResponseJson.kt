package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.tools.json.AnyResponseProvider
import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.conversation.ConversationsDetailsRequest

object ConversationListIdsResponseJson {

    private val jsonProvider = { _: String ->
        """
        |{
        |    "has_more": false,
        |    "paging_state": "AQ==",
        |    "qualified_conversations": [
        |        {
        |            "domain": "anta.wire.link",
        |            "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b"
        |       },
        |        {
        |            "domain": "anta.wire.link",
        |            "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b"
        |        }
        |    ]
        |}
        """.trimIndent()
    }

    val validGetIds = AnyResponseProvider(data = "", jsonProvider)


    val validRequestIds = ValidJsonProvider(
        ConversationsDetailsRequest(emptyList())
    ) {
        """
        |{
        |    "qualified_ids": [
        |        {
        |            "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b",
        |            "domain": "anta.wire.link"
        |        },
        |        {
        |            "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |            "domain": "anta.wire.link"
        |        }
        |    ]
        |}
    """.trimIndent()
    }

}

