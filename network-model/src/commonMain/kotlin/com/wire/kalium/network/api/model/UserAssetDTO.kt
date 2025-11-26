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

package com.wire.kalium.network.api.model

import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@ObjCName("UserAsset")
public data class UserAssetDTO(
    @SerialName("key")
    val key: String,
    @SerialName("size")
    val size: AssetSizeDTO?,
    @SerialName("type")
    val type: UserAssetTypeDTO
)

public fun List<UserAssetDTO>?.getPreviewAssetOrNull(): UserAssetDTO? = this?.firstOrNull { it.size == AssetSizeDTO.PREVIEW }
public fun List<UserAssetDTO>?.getCompleteAssetOrNull(): UserAssetDTO? = this?.firstOrNull { it.size == AssetSizeDTO.COMPLETE }

@Serializable
@ObjCName("AssetSize")
public enum class AssetSizeDTO {
    @SerialName("preview")
    PREVIEW,

    @SerialName("complete")
    COMPLETE;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

@Serializable
@ObjCName("UserAssetType")
public enum class UserAssetTypeDTO {
    @SerialName("image")
    IMAGE;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
