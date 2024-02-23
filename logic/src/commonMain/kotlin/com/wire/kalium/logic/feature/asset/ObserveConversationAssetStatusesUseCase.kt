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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageAssetStatus
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Use case observing statuses of assets when uploading and downloading.
 */
interface ObserveAssetStatusesUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<Map<String, MessageAssetStatus>>
}

internal class ObserveAssetStatusesUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveAssetStatusesUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): Flow<Map<String, MessageAssetStatus>> {
        return messageRepository.observeAssetStatuses(conversationId)
            .map {
                it.fold(
                    { mapOf() },
                    { assetList -> assetList.associateBy { assetStatus -> assetStatus.id } })
            }
            .flowOn(dispatcher.io)
    }
}
