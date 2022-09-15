package com.wire.kalium.network.api.serverypublickey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MLSPublicKeysDTO(
    @SerialName("removal")
    val removal: List<MLSPublicKeyItemDTO>?
)

@Serializable
data class MLSPublicKeyItemDTO(
    val cipherSuite: String,
    val key: String
)
