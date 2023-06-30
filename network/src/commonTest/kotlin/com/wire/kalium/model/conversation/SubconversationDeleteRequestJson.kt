package com.wire.kalium.model.conversation

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest

object SubconversationDeleteRequestJson {

    private val jsonProvider = { serializable: SubconversationDeleteRequest ->
        """
        {
          "epoch": ${serializable.epoch},
          "group_id": "${serializable.groupID}"
        }
        """.trimIndent()
    }

    val v4 = ValidJsonProvider(serializableData = SubconversationDeleteRequest(43UL, "groupid"), jsonProvider)
}
