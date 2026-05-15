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

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.ServiceDAO
import com.wire.kalium.persistence.dao.ServiceEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ServiceRepositoryTest {

    @Test
    fun givenSuccess_whenReadingServiceInfoById_thenSuccessIsPropagated() = runTest {
        val expected = serviceDetails

        val (arrangement, serviceRepository) = Arrangement()
            .withServiceByIdSuccess(serviceIdEntity, serviceEntity)
            .arrange()

        serviceRepository.getServiceById(serviceId).also {
            it.shouldSucceed {
                assertEquals(expected, it)
            }
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.byId(any())
        }
    }

    @Test
    fun givenError_whenReadingServiceInfoById_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withServiceByIdFailure(serviceIdEntity, expected.rootCause)
            .arrange()

        serviceRepository.getServiceById(serviceId).also {
            it.shouldFail {
                assertEquals(expected, it)
            }
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.byId(any())
        }
    }

    @Test
    fun givenSuccess_whenObservingIfServiceIsMember_thenSuccessIsPropagated() = runTest {
        val expected: List<QualifiedIDEntity?> = listOf(null, QualifiedIDEntity("id", "domain"), null)

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveIsMemberSuccess(expected.asFlow())
            .arrange()

        serviceRepository.observeIsServiceMember(serviceId, ConversationId("id", "domain")).test {
            expected.forEach { expectedEmit ->
                val currentValue = awaitItem()
                currentValue.shouldSucceed {
                    assertEquals(expectedEmit?.toModel(), it)
                }
            }
            awaitComplete()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.observeIsServiceMember(any(), any())
        }
    }

    @Test
    fun givenError_whenObservingIfServiceIsMember_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveIsMemberFailure(expected.rootCause)
            .arrange()

        serviceRepository.observeIsServiceMember(serviceId, ConversationId("id", "domain")).first().also {
            it.shouldFail {
                assertEquals(expected, it)
            }
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.observeIsServiceMember(any(), any())
        }
    }

    @Test
    fun givenSuccess_whenObservingAllServices_thenSuccessIsPropagated() = runTest {
        val expectedEntity = listOf(
            serviceEntity.copy(id = BotIdEntity("id1", "domain1")),
            serviceEntity.copy(id = BotIdEntity("id2", "domain2"))
        )

        val expected = listOf(
            serviceDetails.copy(id = ServiceId("id1", "domain1")),
            serviceDetails.copy(id = ServiceId("id2", "domain2"))
        )

        val (arrangement, serviceRepository) = Arrangement()
            .withGetAllServicesSuccess(flowOf(expectedEntity))
            .arrange()

        serviceRepository.observeAllServices().test {
            val currentValue = awaitItem()
            assertIs<Either.Right<List<ServiceDetails>>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.getAllServices()
        }
    }

    @Test
    fun givenError_whenObservingAllServices_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withGetAllServicesFailure(expected.rootCause)
            .arrange()

        serviceRepository.observeAllServices().test {
            val currentValue = awaitItem()
            assertIs<Either.Left<StorageFailure>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.getAllServices()
        }
    }

    @Test
    fun givenSuccess_whenSearchingServicesByName_thenSearchResultIsPropagated() = runTest {
        val expectedEntity = listOf(
            serviceEntity.copy(id = BotIdEntity("id1", "domain1")),
            serviceEntity.copy(id = BotIdEntity("id2", "domain2"))
        )

        val expected = listOf(
            serviceDetails.copy(id = ServiceId("id1", "domain1")),
            serviceDetails.copy(id = ServiceId("id2", "domain2"))
        )

        val (arrangement, serviceRepository) = Arrangement()
            .withSearchServicesByNameSuccess(flowOf(expectedEntity))
            .arrange()

        serviceRepository.searchServicesByName("name").test {
            val currentValue = awaitItem()
            assertIs<Either.Right<List<ServiceDetails>>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.searchServicesByName(any())
        }
    }

    @Test
    fun givenError_whenSearchingServicesByName_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withSearchServicesByNameFailure(expected.rootCause)
            .arrange()

        serviceRepository.searchServicesByName("name").test {
            val currentValue = awaitItem()
            assertIs<Either.Left<StorageFailure>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.searchServicesByName(any())
        }
    }

    private companion object {
        val serviceIdEntity = BotIdEntity("serviceId", "providerId")
        val serviceId = ServiceId("serviceId", "providerId")

        val serviceEntity = ServiceEntity(
            id = serviceIdEntity,
            name = "name",
            description = "description",
            completeAssetId = null,
            previewAssetId = null,
            enabled = true,
            summary = "summary",
            tags = emptyList()
        )

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
        val serviceDAO: ServiceDAO = mock(mode = MockMode.autoUnit)

        private val serviceRepository: ServiceRepository = ServiceDataSource(serviceDAO)

        suspend fun withServiceByIdSuccess(serviceId: BotIdEntity, result: ServiceEntity?) = apply {
            everySuspend {
                serviceDAO.byId(eq(serviceId))
            }.returns(result)
        }

        suspend fun withServiceByIdFailure(serviceId: BotIdEntity, error: Throwable) = apply {
            everySuspend {
                serviceDAO.byId(eq(serviceId))
            }.throws(error)
        }

        suspend fun withObserveIsMemberSuccess(result: Flow<QualifiedIDEntity?>) = apply {
            everySuspend {
                serviceDAO.observeIsServiceMember(any(), any())
            }.returns(result)
        }

        suspend fun withObserveIsMemberFailure(error: Throwable) = apply {
            everySuspend {
                serviceDAO.observeIsServiceMember(any(), any())
            }.throws(error)
        }

        suspend fun withGetAllServicesSuccess(result: Flow<List<ServiceEntity>>) = apply {
            everySuspend {
                serviceDAO.getAllServices()
            }.returns(result)
        }

        suspend fun withGetAllServicesFailure(error: Throwable) = apply {
            everySuspend { serviceDAO.getAllServices() }
                .throws(error)
        }

        suspend fun withSearchServicesByNameSuccess(result: Flow<List<ServiceEntity>>) = apply {
            everySuspend {
                serviceDAO.searchServicesByName(any())
            }.returns(result)
        }

        suspend fun withSearchServicesByNameFailure(error: Throwable) = apply {
            everySuspend {
                serviceDAO.searchServicesByName(any())
            }.throws(error)
        }

        fun arrange() = this to serviceRepository

    }
}
