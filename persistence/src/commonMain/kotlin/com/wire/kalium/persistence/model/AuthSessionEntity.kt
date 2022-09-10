package com.wire.kalium.persistence.model

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        val isOnPremises: Boolean
    )

    data class MetaData(
        val federation: Boolean,
        val apiVersion: Int,
        val domain: String?
    )
}

data class SsoIdEntity(
    val scimExternalId: String?,
    val subject: String?,
    val tenant: String?
)
