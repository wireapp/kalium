/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.service

import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.persistence.dao.BotIdEntity

data class ServiceDetails(
    val id: ServiceId,
    val name: String,
    val description: String,
    val summary: String,
    val enabled: Boolean,
    val tags: List<String>,
    val previewAssetId: UserAssetId?,
    val completeAssetId: UserAssetId?
)

data class ServiceId(
    val id: String,
    val provider: String
) {
    fun toDao(): BotIdEntity = BotIdEntity(id = id, provider = provider)
}
