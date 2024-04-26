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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveIsServiceMemberUseCaseTest {

    @Test
    fun givenServiceIdAndConversationId_whenObservingServiceDetails_thenResultIsObservedServiceDetails() = runTest {
        // given
        val (_, observeServiceDetails) = Arrangement()
            .withObserveIsServiceMemberSuccess(
                serviceId = Arrangement.serviceId,
                conversationId = Arrangement.conversationId
            )
            .arrange()

        // when
        observeServiceDetails.invoke(
            serviceId = Arrangement.serviceId,
            conversationId = Arrangement.conversationId
        ).first().shouldSucceed { result ->
            assertEquals(Arrangement.userId, result)
        }
    }

    private class Arrangement {

        @Mock
        private val serviceRepository = mock(ServiceRepository::class)

        private val observeIsServiceMember = ObserveIsServiceMemberUseCaseImpl(
            serviceRepository = serviceRepository
        )

        fun arrange() = this to observeIsServiceMember

        suspend fun withObserveIsServiceMemberSuccess(
            serviceId: ServiceId,
            conversationId: ConversationId
        ) = apply {
            coEvery {
                serviceRepository.observeIsServiceMember(eq(serviceId), eq(conversationId))
            }.returns(flowOf(Either.Right(userId)))
        }

        companion object {
            const val SERVICE_ID = "serviceId"
            const val PROVIDER_ID = "providerId"

            val userId = QualifiedID(
                value = "userValue",
                domain = "userDomain"
            )

            val serviceId = ServiceId(
                id = SERVICE_ID,
                provider = PROVIDER_ID
            )

            val conversationId = ConversationId(
                value = "conversationValue",
                domain = "conversationDomain"
            )
        }
    }
}
