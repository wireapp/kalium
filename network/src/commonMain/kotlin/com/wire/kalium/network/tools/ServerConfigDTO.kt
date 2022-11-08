package com.wire.kalium.network.tools

data class ServerConfigDTO(
    val id: String,
    val links: Links,
    val metaData: MetaData
) {
    data class Links(
        val api: String,
        val accounts: String,
        val webSocket: String,
        val blackList: String,
        val teams: String,
        val website: String,
        val title: String,
        val isOnPremises: Boolean,
        val proxy: Proxy?
    )

    data class MetaData(
        val federation: Boolean,
        val commonApiVersion: ApiVersionDTO,
        val domain: String?
    )

    data class Proxy(
        val needsAuthentication: Boolean,
        val proxyApi: String,
        val proxyPort: Int
    )
}

fun isProxyRequired(serverConfigDTOProxy: ServerConfigDTO.Proxy?): Boolean {
    return serverConfigDTOProxy != null
}

sealed class ApiVersionDTO(open val version: Int) {

    sealed class Invalid(override val version: Int) : ApiVersionDTO(version) {
        object New : Invalid(NEW_API_VERSION_NUMBER)
        object Unknown : Invalid(UNKNOWN_API_VERSION_NUMBER)
    }

    data class Valid(override val version: Int) : ApiVersionDTO(version)

    companion object {
        fun fromInt(value: Int): ApiVersionDTO {
            return if (value >= MINIMUM_VALID_API_VERSION) Valid(value)
            else if (value == NEW_API_VERSION_NUMBER) Invalid.New
            else Invalid.Unknown
        }

        const val NEW_API_VERSION_NUMBER = -1
        const val UNKNOWN_API_VERSION_NUMBER = -2
        const val MINIMUM_VALID_API_VERSION = 0
    }
}
