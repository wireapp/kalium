package com.wire.kalium.network.api.asset

import kotlinx.serialization.Serializable

@Serializable
data class AssetResponse(
    val key: String,
    val domain: String,
    val expires: String?,
    val token: String?
)
