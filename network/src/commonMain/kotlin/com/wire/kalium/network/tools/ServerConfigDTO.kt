package com.wire.kalium.network.tools

import io.ktor.http.Url

data class WireServerDTO(
    val id: String,
    val links: Links,
    val metaData: MetaData
) {
    data class Links(
        val api: Url,
        val accounts: Url,
        val webSocket: Url,
        val blackList: Url,
        val teams: Url,
        val website: Url,
        val title: String
    )

    data class MetaData(
        val federation: Boolean,
        val commonApiVersion: ApiVersionDTO,
        val domain: String?
    )
}

sealed class ApiVersionDTO(open val version: Int) {
    sealed class Invalid(override val version: Int): ApiVersionDTO(version) {
        object New : Invalid(NEW_API_VERSION_NUMBER)
        object Unknown : Invalid(UNKNOWN_API_VERSION_NUMBER)
    }
    data class Valid(override val version: Int) : ApiVersionDTO(version)

    companion object {
        const val NEW_API_VERSION_NUMBER = -1
        const val UNKNOWN_API_VERSION_NUMBER = -2
        const val MINIMUM_VALID_API_VERSION = 0
    }
}


@Deprecated("old model", replaceWith = ReplaceWith("com.wire.kalium.logic.configuration.server.WireServer"))
data class ServerConfigDTO(
    val apiBaseUrl: Url,
    val accountsBaseUrl: Url,
    val webSocketBaseUrl: Url,
    val blackListUrl: Url,
    val teamsUrl: Url,
    val websiteUrl: Url,
    val title: String,
    val apiVersion: Int
)
