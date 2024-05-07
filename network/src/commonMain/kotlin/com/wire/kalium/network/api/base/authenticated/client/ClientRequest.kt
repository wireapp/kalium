/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.api.base.authenticated.client

import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO.Unknown
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.api.base.model.MLSPublicKey
import com.wire.kalium.network.api.base.model.UserId
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
    @SerialName("model") val model: String?,
    @SerialName("cookie") val cookieLabel: String?,
    @SerialName("verification_code") val secondFactorVerificationCode: String? = null,
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
data class UpdateClientMlsPublicKeysRequest(
    @SerialName("mls_public_keys") val mlsPublicKeys: Map<MLSPublicKeyTypeDTO, MLSPublicKey>
)

@Serializable
data class UpdateClientCapabilitiesRequest(
    @SerialName("capabilities") val capabilities: List<ClientCapabilityDTO>
)

@Serializable
enum class MLSPublicKeyTypeDTO {
<<<<<<< HEAD
=======
    @SerialName("ecdsa_secp256r1_sha256")
    ECDSA_SECP256R1_SHA256,
    @SerialName("ecdsa_secp384r1_sha384")
    ECDSA_SECP384R1_SHA384,
    @SerialName("ecdsa_secp521r1_sha512")
    ECDSA_SECP521R1_SHA512,
    @SerialName("ed448")
    ED448,
>>>>>>> d726d685a9 (feat: set the correct external sender key when creating MLS conversation (#2735))
    @SerialName("ed25519")
    ED25519;
}
