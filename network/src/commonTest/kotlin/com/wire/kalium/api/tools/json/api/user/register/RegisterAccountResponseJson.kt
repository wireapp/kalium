package com.wire.kalium.api.tools.json.api.user.register

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.model.UserDTO
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object RegisterAccountResponseJson {

    private val jsonProvider = { serializable: UserDTO ->
        buildJsonObject {
            put("id", serializable.nonQualifiedId)
            put("name", serializable.name)
            putJsonArray("assets") {
                if (serializable.assets.isNotEmpty()) {
                    addJsonObject {
                        serializable.assets.forEach { userAsset ->
                            put("key", userAsset.key)
                            put("type", userAsset.type.toString())
                            userAsset.size?.let { put("size", it.toString()) }
                        }
                    }
                }
            }
            serializable.accentId?.let { put("accent_id", it) }
            serializable.deleted?.let { put("deleted", it) }
            serializable.email?.let { put("email", it) }
            serializable.handle?.let { put("handle", it) }
            serializable.service?.let { service ->
                putJsonObject("service") {
                    service.id?.let { put("id", it) }
                    service.provider?.let { put("provider", it) }
                }
            }
            serializable.teamId?.let { put("team", it) }
        }.toString()
    }

    val validRegisterResponse = ValidJsonProvider(
        UserDTO(
            id = "user_id",
            name = "user_name_123",
            accentId = null,
            assets = listOf(),
            deleted = null,
            email = null,
            handle = null,
            service = null,
            teamId = null
        ), jsonProvider
    )
}
