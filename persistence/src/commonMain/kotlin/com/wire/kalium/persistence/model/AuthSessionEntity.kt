package com.wire.kalium.persistence.model

data class ServerConfigEntity(
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
        val apiVersion: Int,
        val domain: String?
    )

    data class Proxy(
        val needsAuthentication: Boolean,
        val proxyApi: String,
        val proxyPort: Int
    )
}

data class SsoIdEntity(
    val scimExternalId: String?,
    val subject: String?,
    val tenant: String?
)
