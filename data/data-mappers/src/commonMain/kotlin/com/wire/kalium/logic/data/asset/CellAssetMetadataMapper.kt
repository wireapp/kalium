/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.protobuf.messages.CellAsset
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun CellAsset.InitialMetaData<*>.toModel(): AssetContent.AssetMetadata = when (this) {
    is CellAsset.InitialMetaData.Image -> Image(value.width, value.height)
    is CellAsset.InitialMetaData.Audio -> Audio(value.durationInMillis, normalizedLoudness = null)
    is CellAsset.InitialMetaData.Video -> Video(value.width, value.height, value.durationInMillis)
}
