package com.wire.kalium.logic.util

import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface JsonSerializable {
    fun toJson(): String = KtxSerializer.json.encodeToString(this)
    fun fromJson(jsonString: String): Any = KtxSerializer.json.decodeFromString(jsonString)
}
