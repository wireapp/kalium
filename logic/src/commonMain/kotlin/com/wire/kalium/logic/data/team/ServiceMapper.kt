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
package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.model.ServiceDetailDTO
import com.wire.kalium.network.api.base.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.base.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.ServiceEntity

internal class ServiceMapper {
    fun mapToServiceEntity(
        dto: ServiceDetailDTO,
        selfId: UserId
    ): ServiceEntity = with(dto) {
        ServiceEntity(
            id = BotIdEntity(id = id, provider = provider),
            name = name,
            description = description,
            summary = summary,
            tags = tags,
            enabled = enabled,
            previewAssetId = assets?.getPreviewAssetOrNull()?.let { QualifiedIDEntity(it.key, selfId.domain) },
            completeAssetId = assets?.getCompleteAssetOrNull()?.let { QualifiedIDEntity(it.key, selfId.domain) }
        )
    }
}
