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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.common.functional.Either
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SearchServiceByNameUseCaseTest {

    @Test
    fun givenSuccess_whenSearchingServiceByName_thenResultIsServiceDetails() = runTest {
        val expected: List<ServiceDetails> = listOf(
            serviceDetails.copy(id = ServiceId("id1", "providerId")),
            serviceDetails.copy(id = ServiceId("id2", "providerId"))
        )
        // given
        val (arrangement, searchServiceByNameUseCase) = Arrangement()
            .withSearchServiceByName("query", flowOf(Either.Right(expected)))
            .arrange()

        // when
        searchServiceByNameUseCase("query").first().also {
            assertEquals(expected, it)
        }
    }

    @Test
    fun givenError_whenSearchingServiceByName_thenResultIsEmpty() = runTest {
        val error = StorageFailure.DataNotFound
        // given
        val (arrangement, searchServiceByNameUseCase) = Arrangement()
            .withSearchServiceByName("query", flowOf(Either.Left(error)))
            .arrange()

        // when
        searchServiceByNameUseCase("query").first().also {
            assertEquals(emptyList(), it)
        }
    }

    private companion object {
        val serviceId = ServiceId("serviceId", "providerId")

        val serviceDetails = ServiceDetails(
            id = serviceId,
            name = "name",
            description = "description",
            completeAssetId = null,
            previewAssetId = null,
            enabled = true,
            summary = "summary",
            tags = emptyList()
        )
    }

    private class Arrangement {

        val serviceRepository: ServiceRepository = mock(ServiceRepository::class)

        private val searchServiceByNameUseCase = SearchServicesByNameUseCaseImpl(serviceRepository)

        suspend fun withSearchServiceByName(query: String, result: Flow<Either<StorageFailure, List<ServiceDetails>>>) = apply {
            coEvery {
                serviceRepository.searchServicesByName(eq(query))
            }.returns(result)
        }

        fun arrange() = this to searchServiceByNameUseCase
    }
}
