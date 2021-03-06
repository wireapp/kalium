package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.api.MLSPublicKey
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.prekey.PreKeyDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterClientRequest(
    @SerialName("password") val password: String?,
    @SerialName("prekeys") val preKeys: List<PreKeyDTO>,
    @SerialName("lastkey") val lastKey: PreKeyDTO,
    @SerialName("class") val deviceType: DeviceTypeDTO?,
    @SerialName("type") val type: ClientTypeDTO, // 'temporary', 'permanent', 'legalhold'
    @SerialName("label") val label: String?,
    @SerialName("capabilities") val capabilities: List<ClientCapabilityDTO>?,
    @SerialName("model") val model: String?
)

@Serializable
data class Capabilities(
    @SerialName("capabilities") val capabilities: List<ClientCapabilityDTO>
)

@Serializable
enum class ClientTypeDTO {
    @SerialName("temporary")
    Temporary,

    @SerialName("permanent")
    Permanent,

    @SerialName("legalhold")
    LegalHold;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

/**
 * The type of device where the client is running.
 * In case the backend returns null, nothing, or any other unknown value, [Unknown] is used.
 */
@Serializable
enum class DeviceTypeDTO {
    @SerialName("phone")
    Phone,

    @SerialName("tablet")
    Tablet,

    @SerialName("desktop")
    Desktop,

    @SerialName("legalhold")
    LegalHold,

    @SerialName("unknown")
    Unknown;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

@Serializable
enum class ClientCapabilityDTO {
    @SerialName("legalhold-implicit-consent")
    LegalHoldImplicitConsent {
        override fun toString(): String {
            return "legalhold-implicit-consent"
        }
    }
}

@Serializable
data class ListClientsOfUsersRequest(
    @SerialName("qualified_users") val users: List<UserId>
)

@Serializable
internal data class PasswordRequest(
    @SerialName("password") val password: String?
)

@Serializable
data class UpdateClientRequest(
    @SerialName("mls_public_keys") val mlsPublicKeys: Map<MLSPublicKeyTypeDTO, MLSPublicKey>
)

@Serializable
enum class MLSPublicKeyTypeDTO {
    @SerialName("ed25519")
    ED25519;
}
