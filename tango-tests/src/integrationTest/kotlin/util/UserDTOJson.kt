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

package util

import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.model.UserId
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object UserDTOJson {

    private val jsonProvider = { serializable: SelfUserDTO ->
        buildJsonObject {
            put("accent_id", serializable.accentId)
            put("id", serializable.nonQualifiedId)
            putJsonObject("qualified_id") {
                put("id", serializable.id.value)
                put("domain", serializable.id.domain)
            }
            put("name", serializable.name)
            put("locale", serializable.locale)
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
            serializable.deleted?.let { put("deleted", it) }
            serializable.email?.let { put("email", it) }
            serializable.phone?.let { put("phone", it) }
            serializable.expiresAt?.let { put("expires_at", it) }
            serializable.handle?.let { put("handle", it) }
            serializable.service?.let { service ->
                putJsonObject("service") {
                    put("id", service.id)
                    put("provider", service.provider)
                }
            }
            serializable.teamId?.let { put("team", it) }
            serializable.managedByDTO?.let { put("managed_by", it.toString()) }
            serializable.ssoID?.let { userSsoID ->
                putJsonObject("sso_id") {
                    userSsoID.subject?.let { put("subject", it) }
                    userSsoID.scimExternalId?.let { put("scim_external_id", it) }
                    userSsoID.tenant?.let { put("tenant", it) }
                }
            }
        }.toString()
    }

    fun createValid(userDTO: SelfUserDTO) = ValidJsonProvider(userDTO, jsonProvider)

    val valid = ValidJsonProvider(
        SelfUserDTO(
            id = UserId("user_id", "domain.com"),
            name = "user_name_123",
            accentId = 2,
            assets = listOf(),
            deleted = null,
            email = null,
            handle = null,
            service = null,
            teamId = null,
            expiresAt = "",
            nonQualifiedId = "",
            locale = "",
            managedByDTO = null,
            phone = null,
            ssoID = null,
            supportedProtocols = null
        ), jsonProvider
    )
}
