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
package com.wire.kalium.logic.data.app

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.AppEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity

internal interface AppMapper {

    fun fromUserProfileToAppEntity(userProfileDTO: UserProfileDTO): AppEntity
    fun fromDaoToModel(appEntity: AppEntity): AppDetails
    fun toServiceDetails(appDetails: AppDetails): ServiceDetails
}

internal class AppMapperImpl : AppMapper {

    override fun fromUserProfileToAppEntity(userProfileDTO: UserProfileDTO): AppEntity {
        return AppEntity(
            id = userProfileDTO.id.toDao(),
            name = userProfileDTO.name,
            description = userProfileDTO.app?.description.orEmpty(),
            category = userProfileDTO.app?.category,
            previewAssetId = userProfileDTO.assets.getPreviewAssetOrNull()?.let {
                QualifiedIDEntity(
                    value = it.key,
                    domain = userProfileDTO.id.domain
                )
            },
            completeAssetId = userProfileDTO.assets.getCompleteAssetOrNull()?.let {
                QualifiedIDEntity(
                    value = it.key,
                    domain = userProfileDTO.id.domain
                )
            }
        )
    }

    override fun fromDaoToModel(appEntity: AppEntity): AppDetails = with(appEntity) {
        AppDetails(
            id = appEntity.id.toModel(),
            name = appEntity.name,
            description = appEntity.description,
            category = appEntity.category,
            previewAssetId = appEntity.previewAssetId?.let { asset ->
                UserAssetId(
                    value = asset.value,
                    domain = asset.domain
                )
            },
            completeAssetId = appEntity.completeAssetId?.let { asset ->
                UserAssetId(
                    value = asset.value,
                    domain = asset.domain
                )
            }
        )
    }

    override fun toServiceDetails(appDetails: AppDetails): ServiceDetails =
        ServiceDetails(
            id = ServiceId(
                id = appDetails.id.value,
                provider = appDetails.id.domain
            ),
            name = appDetails.name,
            description = appDetails.description,
            summary = "",
            enabled = true,
            tags = emptyList(),
            previewAssetId = appDetails.previewAssetId,
            completeAssetId = appDetails.completeAssetId
        )
}
