package com.wire.kalium.network.api.base.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String,
    @SerialName("label") val label: String
)
