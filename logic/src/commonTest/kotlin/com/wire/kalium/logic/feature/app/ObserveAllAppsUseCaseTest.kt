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
package com.wire.kalium.logic.feature.app

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.app.AppDetails
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class ObserveAllAppsUseCaseTest {

    @Test
    fun givenSuccess_whenObservingAllApps_thenResultIsServiceDetails() = runTest {
        // given
        val expected: List<ServiceDetails> = listOf(Arrangement.serviceDetails)

        val (_, observeAllApps) = Arrangement()
            .withObserveAllApps(
                flowOf(
                    Either.Right(listOf(Arrangement.appDetails))
                )
            )
            .arrange()

        // when
        observeAllApps().first().also {
            // then
            assertEquals(expected, it)
        }
    }

    @Test
    fun givenError_whenObservingAllApps_thenResultIsEmpty() = runTest {
        // given
        val error = StorageFailure.DataNotFound

        val (_, observeAllApps) = Arrangement()
            .withObserveAllApps(
                flowOf(
                    Either.Left(error)
                )
            )
            .arrange()

        // when
        observeAllApps().first().also {
            // then
            assertEquals(emptyList(), it)
        }
    }

    private class Arrangement {
        private val appRepository = mock(AppRepository::class)

        private val useCase: ObserveAllAppsUseCase = ObserveAllAppsUseCaseImpl(
            appRepository = appRepository
        )

        suspend fun withObserveAllApps(result: Flow<Either<StorageFailure, List<AppDetails>>>) = apply {
            coEvery {
                appRepository.observeAllApps()
            }.returns(result)
        }

        fun arrange() = this to useCase

        companion object {
            val APP_ID = QualifiedID(
                value = Uuid.random().toString(),
                domain = "wire.com"
            )
            const val APP_NAME = "App Name"
            const val APP_DESCRIPTION = "App Description"
            const val APP_CATEGORY = "DEVELOPER"

            val appDetails = AppDetails(
                id = APP_ID,
                name = APP_NAME,
                description = APP_DESCRIPTION,
                category = APP_CATEGORY,
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
        }
    }
}
