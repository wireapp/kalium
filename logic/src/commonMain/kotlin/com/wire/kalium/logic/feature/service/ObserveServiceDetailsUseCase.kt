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
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing service details
 * and verifying if it is a member of given conversation
 * @param serviceId contains service ID and Provider
 * @param conversationId ID of the conversation service will be seen, added or removed.
 */
interface ObserveServiceDetailsUseCase {
    suspend operator fun invoke(
        serviceId: ServiceId,
        conversationId: ConversationId
    ): Flow<ObservedServiceDetails?>
}

class ObserveServiceDetailsUseCaseImpl internal constructor(
    private val serviceRepository: ServiceRepository
) : ObserveServiceDetailsUseCase {

    override suspend fun invoke(serviceId: ServiceId, conversationId: ConversationId): Flow<ObservedServiceDetails?> =
        serviceRepository.observeServiceDetails(
            serviceId = serviceId,
            conversationId = conversationId
        )
}


