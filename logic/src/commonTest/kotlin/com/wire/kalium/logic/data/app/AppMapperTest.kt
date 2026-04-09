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

import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.network.api.model.AppCategoryDTO
import com.wire.kalium.network.api.model.AppDTO
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedID
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.persistence.dao.AppCategoryEntity
import com.wire.kalium.persistence.dao.AppEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AppMapperTest {

    @Test
    fun givenUserProfileDTO_whenMappingToAppEntity_thenReturnAppEntity() = runTest {
        // given
        val (_, appMapper) = Arrangement()
            .arrange()

        // when
        val result = appMapper.fromUserProfileToAppEntity(
            userProfileDTO = Arrangement.userProfileDTO
        )

        // then
        assertEquals(Arrangement.appEntity, result)
    }

    @Test
    fun givenAppEntity_whenMappingToModel_thenReturnAppDetails() = runTest {
        // given
        val (_, appMapper) = Arrangement()
            .arrange()

        // when
        val result = appMapper.fromDaoToModel(
            appEntity = Arrangement.appEntity
        )

        // then
        assertEquals(Arrangement.appDetails, result)
    }

    @Test
    fun givenAppCategoryDTO_whenMappingToEntity_thenReturnAppCategoryEntity() = runTest {
        // given
        val (_, appMapper) = Arrangement()
            .arrange()

        // when
        val result = appMapper.fromCategoryDTOToEntity(
            categoryDTO = Arrangement.userProfileDTO.app?.category
        )

        // then
        assertEquals(Arrangement.appEntity.category, result)
    }

    @Test
    fun givenAppCategoryEntity_whenMappingToModel_thenReturnAppCategory() = runTest {
        // given
        val (_, appMapper) = Arrangement()
            .arrange()

        // when
        val result = appMapper.fromCategoryEntityToModel(
            appCategoryEntity = Arrangement.appEntity.category
        )

        // then
        assertEquals(Arrangement.appDetails.category, result)
    }

    @Test
    fun givenAppDetails_whenMappingToServiceDetails_thenReturnServiceDetails() = runTest {
        // given
        val (_, appMapper) = Arrangement()
            .arrange()

        // when
        val result = appMapper.toServiceDetails(
            appDetails = Arrangement.appDetails
        )

        // then
        assertEquals(Arrangement.serviceDetails, result)
    }

    private class Arrangement {

        private val appMapper = AppMapperImpl()

        fun arrange() = this to appMapper

        companion object {
            val APP_ID = QualifiedID(
                value = Uuid.random().toString(),
                domain = "wire.com"
            )
            val APP_ID_ENTITY = PersistenceQualifiedId(
                value = APP_ID.value,
                domain = APP_ID.domain
            )
            const val APP_NAME = "App Name"
            const val APP_DESCRIPTION = "App Description"
            val APP_CATEGORY = AppCategory.DEVELOPER
            val APP_CATEGORY_ENTITY = AppCategoryEntity.DEVELOPER
            val APP_CATEGORY_DTO = AppCategoryDTO.DEVELOPER

            val appDetails = AppDetails(
                id = APP_ID,
                name = APP_NAME,
                description = APP_DESCRIPTION,
                category = APP_CATEGORY,
                previewAssetId = null,
                completeAssetId = null
            )

            val appEntity = AppEntity(
                id = APP_ID_ENTITY,
                name = APP_NAME,
                description = APP_DESCRIPTION,
                category = APP_CATEGORY_ENTITY,
                previewAssetId = null,
                completeAssetId = null
            )

            val serviceDetails = ServiceDetails(
                id = ServiceId(
                    id = APP_ID.value,
                    provider = APP_ID.domain
                ),
                name = APP_NAME,
                description = APP_DESCRIPTION,
                summary = "",
                enabled = true,
                tags = emptyList(),
                previewAssetId = null,
                completeAssetId = null
            )

            val userProfileDTO = UserProfileDTO(
                id = NetworkQualifiedID(
                    value = APP_ID.value,
                    domain = APP_ID.domain
                ),
                name = APP_NAME,
                handle = null,
                teamId = Uuid.random().toString(),
                accentId = 0,
                assets = emptyList(),
                deleted = false,
                email = null,
                expiresAt = null,
                nonQualifiedId = APP_ID.value,
                service = null,
                supportedProtocols = null,
                legalHoldStatus = LegalHoldStatusDTO.DISABLED,
                type = UserTypeDTO.APP,
                app = AppDTO(
                    description = APP_DESCRIPTION,
                    category = APP_CATEGORY_DTO
                )
            )
        }
    }
}
