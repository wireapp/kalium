/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.featureConfig

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CellsModel(
    @SerialName("status")
    val status: Status,
)

@Serializable
data class CellsInternalModel(
    @SerialName("status")
    val status: Status,
    @SerialName("config")
    val config: CellsInternalConfigModel,
)

@Serializable
data class CellsInternalConfigModel(
    @SerialName("backend")
    val backend: CellsInternalBackendConfigModel?,
)

@Serializable
data class CellsInternalBackendConfigModel(
    @SerialName("url")
    val url: String,
)
