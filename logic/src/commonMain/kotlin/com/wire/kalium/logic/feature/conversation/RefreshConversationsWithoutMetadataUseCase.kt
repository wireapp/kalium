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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.withContext

/**
 * Refresh conversations without metadata, only if necessary.
 */
@Mockable
interface RefreshConversationsWithoutMetadataUseCase {
    suspend operator fun invoke()
}

internal class RefreshConversationsWithoutMetadataUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : RefreshConversationsWithoutMetadataUseCase {
    override suspend fun invoke() = withContext(dispatchers.io) {
        conversationRepository.syncConversationsWithoutMetadata()
            .fold({
                kaliumLogger.w("Error while syncing conversations without metadata $it")
            }) {
                kaliumLogger.d("Finished syncing conversations without metadata")
            }
    }
}
