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
import dev.mokkery.answering.returns
import dev.mokkery.mock
import dev.mokkery.everySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class GetAppByIdUseCaseTest {

    @Test
    fun givenAppId_whenGettingAppById_thenReturnServiceDetails() = runTest {
        // given
        val (_, getAppById) = Arrangement()
            .withGetAppByIdSuccess(appId = Arrangement.APP_ID)
            .arrange()

        // when
        val result = getAppById(appId = Arrangement.APP_ID)

        // then
        assertIs<ServiceDetails>(result)
        assertEquals(Arrangement.serviceDetails, result)
    }

    @Test
    fun givenAppId_whenGettingAppByIdAndDoesntExist_thenReturnsStorageFailure() = runTest {
        // given
        val (_, getAppById) = Arrangement()
            .withGetAppByIdFailsWithStorageFailure(appId = Arrangement.APP_ID)
            .arrange()

        // when
        val result = getAppById(appId = Arrangement.APP_ID)

        // then
        assertEquals(null, result)
    }

    @Test
    fun givenAppId_whenGettingAppByIdReturnsNull_thenReturnNull() = runTest {
        // given
        val (_, getAppById) = Arrangement()
            .withGetAppByIdReturnsNull(appId = Arrangement.APP_ID)
            .arrange()

        // when
        val result = getAppById(appId = Arrangement.APP_ID)

        // then
        assertEquals(null, result)
    }

    private class Arrangement {
        private val appRepository = mock<AppRepository>()

        private val getAppById = GetAppByIdUseCaseImpl(
            appRepository = appRepository
        )

        fun arrange() = this to getAppById

        suspend fun withGetAppByIdSuccess(
            appId: QualifiedID
        ) = apply {
            everySuspend {
                appRepository.getAppById(appId)
            } returns Either.Right(appDetails)
        }

        suspend fun withGetAppByIdFailsWithStorageFailure(
            appId: QualifiedID
        ) = apply {
            everySuspend {
                appRepository.getAppById(appId)
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withGetAppByIdReturnsNull(
            appId: QualifiedID
        ) = apply {
            everySuspend {
                appRepository.getAppById(appId)
            }.returns(Either.Right(null))
        }

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
