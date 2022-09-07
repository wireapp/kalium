package com.wire.kalium.network.api.versioning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionInfoDTO(
    @SerialName("development") val developmentSupported: List<Int>,
    @SerialName("domain") val domain: String?,
    @SerialName("federation") val federation: Boolean,
    @SerialName("supported") val supported: List<Int>
) {
    constructor() : this(listOf(0), null, false, listOf(0))
}
