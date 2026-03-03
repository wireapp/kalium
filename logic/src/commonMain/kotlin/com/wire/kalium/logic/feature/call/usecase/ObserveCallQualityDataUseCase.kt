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

import com.wire.kalium.logic.data.call.CallQualityData
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe the [CallQualityData] for a given [ConversationId] or null if there is no such data.
 */
public interface ObserveCallQualityDataUseCase {
    public operator fun invoke(conversationId: ConversationId): Flow<CallQualityData>
}

internal class ObserveCallQualityDataUseCaseImpl internal constructor(
    private val callRepository: CallRepository
) : ObserveCallQualityDataUseCase {

    override operator fun invoke(conversationId: ConversationId): Flow<CallQualityData> =
        callRepository.observeCallQualityData(conversationId)
}
