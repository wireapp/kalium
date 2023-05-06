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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.service.ObservedServiceDetails
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.network.api.base.model.ServiceDetailDTO
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ServiceEntity
import com.wire.kalium.persistence.dao.ServiceViewEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveServiceDetailsUseCaseTest {

    @Test
    fun givenServiceIdAndConversationId_whenObservingServiceDetails_thenResultIsObservedServiceDetails() = runTest {
        // given
        val (arrangement, observeServiceDetails) = Arrangement()
            .withObservedServiceDetails(
                serviceId = Arrangement.serviceId,
                conversationId = Arrangement.conversationId
            )
            .arrange()

        // when
        val result = observeServiceDetails.invoke(
            serviceId = Arrangement.serviceId,
            conversationId = Arrangement.conversationId
        ).first()

        // then
        assertIs<ObservedServiceDetails>(result)
        assertEquals(Arrangement.observedServiceDetails, result)
    }

    private class Arrangement {

        @Mock
        private val serviceRepository = configure(mock(classOf<ServiceRepository>())) {
            stubsUnitByDefault = true
        }

        private val observeServiceDetails = ObserveServiceDetailsUseCaseImpl(
            serviceRepository = serviceRepository
        )

        fun arrange() = this to observeServiceDetails

        fun withObservedServiceDetails(
            serviceId: ServiceId,
            conversationId: ConversationId
        ) = apply {
            given(serviceRepository)
                .suspendFunction(serviceRepository::observeServiceDetails)
                .whenInvokedWith(eq(serviceId), eq(conversationId))
                .thenReturn(flowOf(observedServiceDetails))
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

            val conversationId = ConversationId(
                value = "conversationValue",
                domain = "conversationDomain"
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

            val observedServiceDetails = ObservedServiceDetails(
                service = serviceDetails,
                isMember = true
            )
        }
    }
}
