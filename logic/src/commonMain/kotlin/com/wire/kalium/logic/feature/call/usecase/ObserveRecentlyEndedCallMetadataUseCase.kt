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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.RecentlyEndedCallMetadata
import kotlinx.coroutines.flow.Flow
import com.wire.kalium.logic.data.id.ConversationId

/**
 * Use case to observe recently ended call metadata. This gives us all metadata assigned to a call.
 * Used mainly for analytics.
 */
interface ObserveRecentlyEndedCallMetadataUseCase {
    suspend operator fun invoke(): Flow<RecentlyEndedCallMetadata>
}

class ObserveRecentlyEndedCallMetadataUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
) : ObserveRecentlyEndedCallMetadataUseCase {
    override suspend fun invoke(): Flow<RecentlyEndedCallMetadata> {
        return callRepository.observeRecentlyEndedCallMetadata()
    }
}
