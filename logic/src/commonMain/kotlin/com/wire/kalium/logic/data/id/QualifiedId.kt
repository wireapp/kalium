package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.util.JsonSerializable
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedID(
    val value: String,
    val domain: String
) : JsonSerializable
