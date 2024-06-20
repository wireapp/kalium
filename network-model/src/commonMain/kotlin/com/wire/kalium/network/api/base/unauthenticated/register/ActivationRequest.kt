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

package com.wire.kalium.network.api.base.unauthenticated.register

import com.wire.kalium.network.api.base.model.AssetKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestActivationRequest(
    @SerialName("email")
    val email: String?,
    @SerialName("locale")
    val locale: String?,
    @SerialName("phone")
    val phone: String?,
    @SerialName("voice_call")
    val voiceCall: Boolean?
)

@Serializable
data class ActivationRequest(
    @SerialName("code")
    val code: String,
    @SerialName("dryrun")
    val dryRun: Boolean?,
    @SerialName("email")
    val email: String?,
    @SerialName("key")
    val key: String?,
    @SerialName("label")
    val label: String?,
    @SerialName("phone")
    val phone: String?
)

@Serializable
data class NewBindingTeamDTO(
    @SerialName("currency")
    val currency: String?,
    @SerialName("icon")
    val iconAssetId: String, // todo(assets): temp fix, we should replace with [AssetId] once domain avb on server config (api-version pr)
    @SerialName("icon_key")
    val iconKey: AssetKey?,
    @SerialName("name")
    val name: String
)
