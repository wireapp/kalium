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
package com.wire.kalium.logic.data.service

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.ServiceDetailDTO
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ServiceEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceMapperTest {

    @Test
    fun givenServiceDTO_whenMappingToEntity_thenReturnServiceEntity() = runTest {
        // given
        val (_, serviceMapper) = Arrangement()
            .arrange()

        // when
        val result = serviceMapper.mapToServiceEntity(
            dto = Arrangement.serviceDetailDTO,
            selfId = Arrangement.selfUserId
        )

        // then
        assertEquals(Arrangement.serviceEntity, result)
    }

    @Test
    fun givenServiceEntity_whenMappingToModel_thenReturnServiceDetails() = runTest {
        // given
        val (_, serviceMapper) = Arrangement()
            .arrange()

        // when
        val result = serviceMapper.fromDaoToModel(
            service = Arrangement.serviceEntity
        )

        // then
        assertEquals(Arrangement.serviceDetails, result)
    }

    @Test
    fun givenServiceIdModel_whenMappingToDaoEntity_thenReturnBotIdEntity() = runTest {
        // given
        val (_, serviceMapper) = Arrangement()
            .arrange()

        // when
        val result = serviceMapper.fromModelToDao(
            serviceId = Arrangement.serviceId
        )

        // then
        assertEquals(Arrangement.botIdEntity, result)
    }

    private class Arrangement {

        private val serviceMapper = ServiceMapper()

        fun arrange() = this to serviceMapper

        companion object {
            const val SERVICE_ID = "serviceId"
            const val PROVIDER_ID = "providerId"
            const val SERVICE_NAME = "Service Name"
            const val SERVICE_DESCRIPTION = "Service Description"
            const val SERVICE_SUMMARY = "Service Summary"
            const val SERVICE_ENABLED = true
            val SERVICE_TAGS = emptyList<String>()

            val selfUserId = UserId(
                value = "userId",
                domain = "userDomain"
            )

            val serviceId = ServiceId(
                id = SERVICE_ID,
                provider = PROVIDER_ID
            )

            val botIdEntity = BotIdEntity(
                id = SERVICE_ID,
                provider = PROVIDER_ID
            )

            val serviceDetailDTO = ServiceDetailDTO(
                enabled = SERVICE_ENABLED,
                assets = null,
                id = SERVICE_ID,
                provider = PROVIDER_ID,
                name = SERVICE_NAME,
                description = SERVICE_DESCRIPTION,
                summary = SERVICE_SUMMARY,
                tags = SERVICE_TAGS
            )

            val serviceEntity = ServiceEntity(
                id = botIdEntity,
                name = SERVICE_NAME,
                description = SERVICE_DESCRIPTION,
                summary = SERVICE_SUMMARY,
                enabled = SERVICE_ENABLED,
                tags = SERVICE_TAGS,
                previewAssetId = null,
                completeAssetId = null
            )

            val serviceDetails = ServiceDetails(
                id = serviceId,
                name = SERVICE_NAME,
                description = SERVICE_DESCRIPTION,
                summary = SERVICE_SUMMARY,
                enabled = SERVICE_ENABLED,
                tags = SERVICE_TAGS,
                previewAssetId = null,
                completeAssetId = null
            )
        }
    }
}
