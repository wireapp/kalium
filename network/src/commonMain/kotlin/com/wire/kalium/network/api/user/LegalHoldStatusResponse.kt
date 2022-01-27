package com.wire.kalium.network.api.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LegalHoldStatusResponse {
    @SerialName("enabled")
    ENABLED,
    @SerialName("pending")
    PENDING,
    @SerialName("disabled")
    DISABLED,
    @SerialName("no_consent")
    NO_CONSENT
}
