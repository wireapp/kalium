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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetServiceByIdUseCaseTest {

    @Test
    fun givenServiceId_whenGettingServiceById_thenReturnServiceDetails() = runTest {
        // given
        val (_, getServiceById) = Arrangement()
            .withGetServiceByIdSuccess(serviceId = Arrangement.serviceId)
            .arrange()

        // when
        val result = getServiceById(serviceId = Arrangement.serviceId)

        // then
        assertIs<ServiceDetails>(result)
        assertEquals(Arrangement.serviceDetails, result)
    }

    private class Arrangement {

        @Mock
        private val serviceRepository = mock(ServiceRepository::class)

        private val getServiceById = GetServiceByIdUseCaseImpl(
            serviceRepository = serviceRepository
        )

        fun arrange() = this to getServiceById

        suspend fun withGetServiceByIdSuccess(
            serviceId: ServiceId
        ) = apply {
            coEvery {
                serviceRepository.getServiceById(eq(serviceId))
            }.returns(Either.Right(serviceDetails))
        }

        companion object {
            const val SERVICE_ID = "serviceId"
            const val PROVIDER_ID = "providerId"
            const val SERVICE_NAME = "Service Name"
            const val SERVICE_DESCRIPTION = "Service Description"
            const val SERVICE_SUMMARY = "Service Summary"
            const val SERVICE_ENABLED = true
            val SERVICE_TAGS = emptyList<String>()

            val serviceId = ServiceId(
                id = SERVICE_ID,
                provider = PROVIDER_ID
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
