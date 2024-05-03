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

package com.wire.kalium.model.conversation

import com.wire.kalium.api.json.AnyResponseProvider
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationsDetailsRequest

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
