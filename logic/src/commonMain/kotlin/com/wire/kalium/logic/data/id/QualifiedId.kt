package com.wire.kalium.logic.data.id

import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class QualifiedID(
    val value: String,
    val domain: String
) {
    fun toJson(): String = Json.encodeToString(this)
    fun fromJson(jsonString: String): QualifiedID = KtxSerializer.json.decodeFromString(jsonString)
}
