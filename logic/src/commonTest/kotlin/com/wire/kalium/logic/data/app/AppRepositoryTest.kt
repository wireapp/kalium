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

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.AppDAO
import com.wire.kalium.persistence.dao.AppEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class AppRepositoryTest {

    @Test
    fun givenSuccess_whenReadingAppDetailsById_thenSuccessIsPropagated() = runTest {
        // given
        val expected = Arrangement.appDetails

        val (arrangement, appRepository) = Arrangement()
            .withAppByIdSuccess(
                Arrangement.APP_ID_ENTITY,
                Arrangement.appDetailsEntity
            )
            .arrange()

        // when
        // then
        appRepository.getAppById(Arrangement.APP_ID).also {
            it.shouldSucceed { actual ->
                assertEquals(expected, actual)
            }
        }

        coVerify {
            arrangement.appDAO.byId(Arrangement.APP_ID_ENTITY)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenReadingAppDetailsById_thenErrorIsPropagated() = runTest {
        // given
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, appRepository) = Arrangement()
            .withAppByIdFailure(
                Arrangement.APP_ID_ENTITY,
                expected.rootCause
            )
            .arrange()

        // when
        // then
        appRepository.getAppById(Arrangement.APP_ID).also {
            it.shouldFail {
                assertEquals(expected, it)
            }
        }

        coVerify {
            arrangement.appDAO.byId(Arrangement.APP_ID_ENTITY)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccess_whenObservingIfAppIsMember_thenSuccessIsPropagated() = runTest {
        val expected: List<QualifiedIDEntity?> = listOf(null, QualifiedIDEntity("id", "domain"), null)

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveIsMemberSuccess(expected.asFlow())
            .arrange()

        serviceRepository.observeIsAppMember(
            Arrangement.APP_ID,
            Arrangement.CONVERSATION_ID
        ).test {
            expected.forEach { expectedEmit ->
                val currentValue = awaitItem()
                currentValue.shouldSucceed {
                    assertEquals(expectedEmit?.toModel(), it)
                }
            }
            awaitComplete()
        }

        coVerify {
            arrangement.appDAO.observeIsAppMember(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenObservingIfAppIsMember_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveIsMemberFailure(expected.rootCause)
            .arrange()

        serviceRepository.observeIsAppMember(
            Arrangement.APP_ID,
            Arrangement.CONVERSATION_ID
        ).first().also {
            it.shouldFail {
                assertEquals(expected, it)
            }
        }

        coVerify {
            arrangement.appDAO.observeIsAppMember(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccess_whenObservingAllApps_thenSuccessIsPropagated() = runTest {
        val expectedEntity = listOf(Arrangement.appDetailsEntity)

        val expected = listOf(Arrangement.appDetails)

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveAllAppsSuccess(flowOf(expectedEntity))
            .arrange()

        serviceRepository.observeAllApps().test {
            val currentValue = awaitItem()
            assertIs<Either.Right<List<AppDetails>>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        coVerify {
            arrangement.appDAO.observeAllApps()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenObservingAllApps_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveAllAppsFailure(expected.rootCause)
            .arrange()

        serviceRepository.observeAllApps().test {
            val currentValue = awaitItem()
            assertIs<Either.Left<StorageFailure>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        coVerify {
            arrangement.appDAO.observeAllApps()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccess_whenSearchingAppsByName_thenSearchResultIsPropagated() = runTest {
        val expectedEntity = listOf(Arrangement.appDetailsEntity)

        val expected = listOf(Arrangement.appDetails)

        val (arrangement, serviceRepository) = Arrangement()
            .withSearchAppsByNameSuccess(flowOf(expectedEntity))
            .arrange()

        serviceRepository.searchAppsByName("name").test {
            val currentValue = awaitItem()
            assertIs<Either.Right<List<AppDetails>>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        coVerify {
            arrangement.appDAO.searchAppsByName(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenSearchingAppsByName_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withSearchAppsByNameFailure(expected.rootCause)
            .arrange()

        serviceRepository.searchAppsByName("name").test {
            val currentValue = awaitItem()
            assertIs<Either.Left<StorageFailure>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        coVerify {
            arrangement.appDAO.searchAppsByName(any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val appDAO = mock(AppDAO::class)
        private val appRepository = AppDataSource(appDAO)


        suspend fun withAppByIdSuccess(appId: PersistenceQualifiedId, result: AppEntity) = apply {
            coEvery {
                appDAO.byId(eq(appId))
            }.returns(result)
        }

        suspend fun withAppByIdFailure(appId: PersistenceQualifiedId, error: Throwable) = apply {
            coEvery {
                appDAO.byId(eq(appId))
            }.throws(error)
        }

        suspend fun withObserveIsMemberSuccess(result: Flow<QualifiedIDEntity?>) = apply {
            coEvery {
                appDAO.observeIsAppMember(any(), any())
            }.returns(result)
        }

        suspend fun withObserveIsMemberFailure(error: Throwable) = apply {
            coEvery {
                appDAO.observeIsAppMember(any(), any())
            }.throws(error)
        }

        suspend fun withObserveAllAppsSuccess(result: Flow<List<AppEntity>>) = apply {
            coEvery {
                appDAO.observeAllApps()
            }.returns(result)
        }

        suspend fun withObserveAllAppsFailure(error: Throwable) = apply {
            coEvery { appDAO.observeAllApps() }
                .throws(error)
        }

        suspend fun withSearchAppsByNameSuccess(result: Flow<List<AppEntity>>) = apply {
            coEvery {
                appDAO.searchAppsByName(any())
            }.returns(result)
        }

        suspend fun withSearchAppsByNameFailure(error: Throwable) = apply {
            coEvery {
                appDAO.searchAppsByName(any())
            }.throws(error)
        }

        fun arrange() = this to appRepository

        companion object {
            val APP_ID = QualifiedID(
                value = Uuid.Companion.random().toString(),
                domain = "wire.com"
            )
            val APP_ID_ENTITY = PersistenceQualifiedId(
                value = APP_ID.value,
                domain = APP_ID.domain
            )
            const val APP_NAME = "App Name"
            const val APP_DESCRIPTION = "App Description"
            const val APP_CATEGORY = "DEVELOPER"

            val CONVERSATION_ID = QualifiedID(
                value = Uuid.Companion.random().toString(),
                domain = "wire.com"
            )

            val appDetails = AppDetails(
                id = APP_ID,
                name = APP_NAME,
                description = APP_DESCRIPTION,
                category = APP_CATEGORY,
                previewAssetId = null,
                completeAssetId = null
            )

            val appDetailsEntity = AppEntity(
                id = APP_ID_ENTITY,
                name = APP_NAME,
                description = APP_DESCRIPTION,
                category = APP_CATEGORY,
                previewAssetId = null,
                completeAssetId = null
            )
        }
    }
}
