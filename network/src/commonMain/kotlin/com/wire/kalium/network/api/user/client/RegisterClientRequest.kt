package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.api.prekey.PreKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterClientRequest(
    @SerialName("password") val password: String,
    @SerialName("prekeys") val preKeys: List<PreKey>,
    @SerialName("lastkey") val lastKey: PreKey,
    @SerialName("class") val deviceType: DeviceType,
    @SerialName("type") val type: ClientType, // 'temporary', 'permanent', 'legalhold'
    @SerialName("label") val label: String,
    @SerialName("capabilities") val capabilities: List<ClientCapability>?,
    @SerialName("model") val model: String
)

@Serializable
data class RegisterClientResponse(
    @SerialName("id") val clientId: String,
    @SerialName("type") val type: ClientType,
    @SerialName("time") val registrationTime: String, // yyyy-mm-ddThh:MM:ss.qqq
    @SerialName("location") val location: LocationResponse?,
    @SerialName("class") val deviceType: DeviceType?,
    @SerialName("label") val label: String?,
    @SerialName("cookie") val cookie: String?,
    @SerialName("capabilities") val capabilities: Capabilities?,
    @SerialName("model") val model: String?
)

@Serializable
data class Capabilities(
    @SerialName("capabilities") val capabilities: List<ClientCapability>
)

@Serializable
enum class ClientType {
    @SerialName("temporary")
    Temporary {
        override fun toString(): String {
            return "temporary"
        }
    },

    @SerialName("permanent")
    Permanent {
        override fun toString(): String {
            return "permanent"
        }
    },

    @SerialName("legalhold")
    LegalHold {
        override fun toString(): String {
            return "legalhold"
        }
    }
}

@Serializable
enum class DeviceType {
    //'phone', 'tablet', 'desktop', 'legalhold'
    @SerialName("phone")
    Phone {
        override fun toString(): String {
            return "phone"
        }
    },

    @SerialName("tablet")
    Tablet {
        override fun toString(): String {
            return "tablet"
        }
    },

    @SerialName("desktop")
    Desktop {
        override fun toString(): String {
            return "desktop"
        }
    },

    @SerialName("legalhold")
    LegalHold {
        override fun toString(): String {
            return "legalhold"
        }
    }
}

@Serializable
enum class ClientCapability {
    @SerialName("legalhold-implicit-consent")
    LegalHoldImplicitConsent {
        override fun toString(): String {
            return "legalhold-implicit-consent"
        }
    }
}
