package com.wire.kalium.network.api.model

import kotlinx.serialization.Serializable

@Serializable
data class AssetResponse(
    val key: String,
    val expires: String?,
    val token: String?
)
