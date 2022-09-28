package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeysDTO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

object MLSPublicKeysResponseJson {

    private val jsonProvider = { serializable: MLSPublicKeysDTO ->
        buildJsonObject {
            serializable.removal?.let {
                put("removal", Json.encodeToJsonElement(it))
            }
        }.toString()
    }

    val valid = ValidJsonProvider(
        MLSPublicKeysDTO(
            mapOf(Pair("ed25519", "gRNvFYReriXbzsGu7zXiPtS8kaTvhU1gUJEV9rdFHVw="))
        ),
        jsonProvider
    )
}
