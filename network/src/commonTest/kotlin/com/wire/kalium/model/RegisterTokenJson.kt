package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.model.PushTokenBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RegisterTokenJson {
    val registerTokenResponse = """ 
            {
             "app":"8218398",
             "client":"123456",
             "token":"oaisjdoiasjd",
             "transport":"GCM"
                }
            """.trimIndent()

    private val jsonProvider = { serializable: PushTokenBody ->
        buildJsonObject {
            put("app", serializable.senderId)
            put("client", serializable.client)
            put("token", serializable.token)
            put("transport", serializable.transport)
        }.toString()
    }

    val validPushTokenRequest =
        ValidJsonProvider(
            PushTokenBody(
                "8218398",
                "123456",
                "oaisjdoiasjd",
                "GCM"
            ), jsonProvider
        )
}
