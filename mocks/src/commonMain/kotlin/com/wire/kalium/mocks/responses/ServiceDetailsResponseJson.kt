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
package com.wire.kalium.mocks.responses

import com.wire.kalium.network.api.base.model.AssetSizeDTO
import com.wire.kalium.network.api.base.model.ServiceDetailDTO
import com.wire.kalium.network.api.base.model.ServiceDetailResponse
import com.wire.kalium.network.api.base.model.UserAssetDTO
import com.wire.kalium.network.api.base.model.UserAssetTypeDTO
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object ServiceDetailsResponseJson {

    private val jsonProvider = { serializable: ServiceDetailResponse ->
        buildJsonObject {
            put("has_more", serializable.hasMore)
            putJsonArray("services") {
                serializable.services.forEach {
                    addJsonObject {
                        put("enabled", it.enabled)
                        put("id", it.id)
                        put("provider", it.provider)
                        put("name", it.name)
                        put("description", it.description)
                        put("summary", it.summary)
                        it.assets?.let { assetsList ->
                            putJsonArray("assets") {
                                assetsList.forEach { asset ->
                                addJsonObject {
                                    put("key", asset.key)
                                    put("type", asset.type.toString())
                                    asset.size?.let { size ->
                                        put("size", size.toString())
                                    }
                                }
                            }
                            }
                        }
                        putJsonArray("tags") {
                            it.tags.forEach { tag ->
                                add(tag)
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    val valid = ValidJsonProvider(
        ServiceDetailResponse(
            true,
            listOf(
                ServiceDetailDTO(
                    true,
                    listOf(
                        UserAssetDTO(
                            "type",
                            AssetSizeDTO.COMPLETE,
                            UserAssetTypeDTO.IMAGE
                        )
                    ),
                    "id",
                    "provider",
                    "name",
                    "description",
                    "summary",
                    listOf("tags")
                )
            )
        ),
        jsonProvider
    )

}
