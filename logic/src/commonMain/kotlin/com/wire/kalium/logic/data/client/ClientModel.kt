package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.data.location.Location
import com.wire.kalium.logic.data.prekey.PreKey
import com.wire.kalium.network.api.user.client.ClientTypeDTO


data class RegisterClientParam(
    val password: String,
    val preKeys: List<PreKey>,
    val lastKey: PreKey,
    val deviceType: DeviceType,
    val type: ClientType, // 'temporary', 'permanent', 'legalhold'
    val label: String,
    val capabilities: List<ClientCapability>?,
    val model: String
)

data class Client(
    val clientId: String,
    val type: ClientType,
    val registrationTime: String, // yyyy-mm-ddThh:MM:ss.qqq
    val location: Location?,
    val deviceType: DeviceType?,
    val label: String?,
    val cookie: String?,
    val capabilities: Capabilities?,
    val model: String?
)

data class Capabilities(
    val capabilities: List<ClientCapability>
)

enum class ClientType {
    Temporary,
    Permanent,
    LegalHold;

    override fun toString(): String {
        return this.name.lowercase()
    }



    fun ClientTypeDTO.toClientType(): ClientType {
        return when(this) {
            ClientTypeDTO.Temporary -> Temporary
            ClientTypeDTO.Permanent -> Permanent
            ClientTypeDTO.LegalHold -> LegalHold
        }
    }
}

enum class DeviceType {
    Phone,
    Tablet,
    Desktop,
    LegalHold;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

enum class ClientCapability {
    LegalHoldImplicitConsent {
        override fun toString(): String {
            return "legalhold-implicit-consent"
        }
    }
}
