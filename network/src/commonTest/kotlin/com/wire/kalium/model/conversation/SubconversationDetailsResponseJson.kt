package com.wire.kalium.model.conversation

import com.wire.kalium.api.json.ValidJsonProvider

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

    val v4 = ValidJsonProvider("", jsonProvider)
}
