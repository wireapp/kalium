package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AssetRetentionType {
    @SerialName("eternal")
    ETERNAL,

    @SerialName("persistent")
    PERSISTENT,

    @SerialName("volatile")
    VOLATILE,

    @SerialName("eternal_infrequent_access")
    ETERNAL_INFREQUENT_ACCESS,

    @SerialName("expiring")
    EXPIRING
}
