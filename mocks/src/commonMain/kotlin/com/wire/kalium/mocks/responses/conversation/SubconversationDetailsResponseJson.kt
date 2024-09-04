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
package com.wire.kalium.mocks.responses.conversation

import com.wire.kalium.mocks.responses.ValidJsonProvider

object SubconversationDetailsResponseJson {

    private val jsonProvider = { _: String ->
        """
        {
          "cipher_suite": 65535,
          "epoch": 184467440737,
          "epoch_timestamp": "2021-05-12T10:52:02.671Z",
          "group_id": "ZXhhbXBsZQo=",
          "members": [
            {
              "client_id": "string",
              "domain": "example.com",
              "user_id": "99db9768-04e3-4b5d-9268-831b6a25c4ab"
            }
          ],
          "parent_qualified_id": {
            "domain": "example.com",
            "id": "99db9768-04e3-4b5d-9268-831b6a25c4ab"
          },
          "subconv_id": "string"
        }
        """.trimIndent()
    }

    val v5 = ValidJsonProvider("", jsonProvider)
}
