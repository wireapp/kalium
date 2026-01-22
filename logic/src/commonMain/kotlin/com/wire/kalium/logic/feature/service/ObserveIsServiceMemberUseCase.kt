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
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public interface ObserveIsServiceMemberUseCase {
    /**
     * This use case is responsible for observing if a service is member of given conversation.
     * @param serviceId contains service ID and Provider.
     * @param conversationId ID of the conversation service will be seen, added or removed.
     * @return a [Flow] of [ObserveIsServiceMemberResult] with Success of a Qualified ID of Service in User table or NULL or an error.
     */
    public suspend operator fun invoke(
        serviceId: ServiceId,
        conversationId: ConversationId
    ): Flow<ObserveIsServiceMemberResult>
}

internal class ObserveIsServiceMemberUseCaseImpl internal constructor(
    private val serviceRepository: ServiceRepository
) : ObserveIsServiceMemberUseCase {

    override suspend fun invoke(serviceId: ServiceId, conversationId: ConversationId): Flow<ObserveIsServiceMemberResult> =
        serviceRepository.observeIsServiceMember(
            serviceId = serviceId,
            conversationId = conversationId
        ).map {
            when (it.isRight()) {
                true -> ObserveIsServiceMemberResult.Success(it.value)
                false -> ObserveIsServiceMemberResult.Failure(it.value)
            }
        }
}

public sealed class ObserveIsServiceMemberResult {
    public data class Success(val userId: UserId?) : ObserveIsServiceMemberResult()
    public data class Failure(val failure: StorageFailure) : ObserveIsServiceMemberResult()
}
