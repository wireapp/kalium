package com.wire.kalium.network.api.versioning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionInfoDTO(
    @SerialName("domain") val domain: String?,
    @SerialName("federation") val federation: Boolean,
    @SerialName("supported") val supported: List<Int>
) {
    constructor(): this(null, false, listOf(0))
}
