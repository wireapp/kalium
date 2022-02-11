package com.wire.kalium.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedHandle(
    @SerialName("domain") val domain: String,
    @SerialName("handle") val handle: String
)
