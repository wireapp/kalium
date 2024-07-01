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

package com.wire.kalium.mocks.responses.connection

import com.wire.kalium.mocks.responses.AnyResponseProvider
import com.wire.kalium.mocks.responses.ValidJsonProvider

object ConnectionResponsesJson {

    object GetConnections {
        private val jsonProvider = { _: String ->
            """
            |{
            |  "connections": [
            |    {
            |      "conversation": "addb6fbf-2bc3-4b59-b428-6fa4c594fb05",
            |      "from": "36ef84a9-837a-4f75-af81-5a2e70e06836",
            |      "last_update": "2022-04-04T16:11:28.388Z",
            |      "qualified_conversation": {
            |        "domain": "staging.zinfra.io",
            |        "id": "addb6fbf-2bc3-4b59-b428-6fa4c594fb05"
            |      },
            |      "qualified_to": {
            |        "domain": "staging.zinfra.io",
            |        "id": "76ebeb16-a849-4be4-84a7-157654b492cf"
            |      },
            |      "status": "accepted",
            |      "to": "76ebeb16-a849-4be4-84a7-157654b492cf"
            |    },
            |    {
            |      "conversation": "af6d3c9a-7934-4790-9ebf-f655e13acc76",
            |      "from": "36ef84a9-837a-4f75-af81-5a2e70e06836",
            |      "last_update": "2022-03-23T16:53:32.515Z",
            |      "qualified_conversation": {
            |        "domain": "staging.zinfra.io",
            |        "id": "af6d3c9a-7934-4790-9ebf-f655e13acc76"
            |      },
            |      "qualified_to": {
            |        "domain": "staging.zinfra.io",
            |        "id": "787db7f1-f5ba-481b-af3e-9c27705a6440"
            |      },
            |      "status": "accepted",
            |      "to": "787db7f1-f5ba-481b-af3e-9c27705a6440"
            |    },
            |    {
            |      "conversation": "f15a944a-b62b-4d9a-aff4-014d78a02294",
            |      "from": "36ef84a9-837a-4f75-af81-5a2e70e06836",
            |      "last_update": "2022-03-25T17:20:13.637Z",
            |      "qualified_conversation": {
            |        "domain": "staging.zinfra.io",
            |        "id": "f15a944a-b62b-4d9a-aff4-014d78a02294"
            |      },
            |      "qualified_to": {
            |        "domain": "staging.zinfra.io",
            |        "id": "ba6b0fa1-32b1-4e25-8072-a71f07bfba5e"
            |      },
            |      "status": "accepted",
            |      "to": "ba6b0fa1-32b1-4e25-8072-a71f07bfba5e"
            |    }
            |  ],
            |  "has_more": false,
            |  "paging_state": "AQ=="
            |}
            """.trimIndent()
        }

        val validGetConnections = AnyResponseProvider(data = "", jsonProvider)
    }

    object CreateConnectionResponse {
        val jsonProvider = ValidJsonProvider(String) {
            """
             {
                  "conversation": "addb6fbf-2bc3-4b59-b428-6fa4c594fb05",
                  "from": "36ef84a9-837a-4f75-af81-5a2e70e06836",
                  "last_update": "2022-04-04T16:11:28.388Z",
                  "qualified_conversation": {
                    "domain": "staging.zinfra.io",
                    "id": "addb6fbf-2bc3-4b59-b428-6fa4c594fb05"
                  },
                  "qualified_to": {
                    "domain": "staging.zinfra.io",
                    "id": "76ebeb16-a849-4be4-84a7-157654b492cf"
                  },
                  "status": "accepted",
                  "to": "76ebeb16-a849-4be4-84a7-157654b492cf"
             }
            """.trimIndent()
        }
    }
}
