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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAllServicesUseCaseTest {

    @Test
    fun givenSuccess_whenObserveAllServices_thenResultIsServiceDetails() = runTest {
        val expected: List<ServiceDetails> = listOf(
            serviceDetails.copy(id = ServiceId("id1", "providerId")),
            serviceDetails.copy(id = ServiceId("id2", "providerId"))
        )

        val (_, observeAllServicesUseCase) = Arrangement()
            .withSelfUserTeamId(Either.Right(TestUser.SELF.teamId))
            .withSyncingServices()
            .withObserveAllServices(flowOf(Either.Right(expected)))
            .arrange()

        observeAllServicesUseCase().first().also {
            assertEquals(expected, it)
        }
    }

    @Test
    fun givenError_whenObserveAllServices_thenResultIsEmpty() = runTest {
        val error = StorageFailure.DataNotFound

        val (_, observeAllServicesUseCase) = Arrangement()
            .withSelfUserTeamId(Either.Right(TestUser.SELF.teamId))
            .withSyncingServices()
            .withObserveAllServices(flowOf(Either.Left(error)))
            .arrange()

        observeAllServicesUseCase().first().also {
            assertEquals(emptyList<ServiceDetails>(), it)
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

        @Mock
        val serviceRepository: ServiceRepository = mock(ServiceRepository::class)

        @Mock
        val teamRepository: TeamRepository = mock(TeamRepository::class)

        @Mock
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        private val useCase: ObserveAllServicesUseCase = ObserveAllServicesUseCaseImpl(
            serviceRepository,
            teamRepository,
            selfTeamIdProvider
        )

        fun withObserveAllServices(result: Flow<Either<StorageFailure, List<ServiceDetails>>>) = apply {
            given(serviceRepository)
                .suspendFunction(serviceRepository::observeAllServices)
                .whenInvoked()
                .then { result }
        }

        fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>) = apply {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .then { either }
        }

        fun withSyncingServices() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::syncServices)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to useCase
    }
}
