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

package com.wire.kalium.logic.feature.message.draft

import com.wire.kalium.logic.data.message.draft.MessageDraft
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Save message draft for given conversation
 * @param messageDraft message payload to save
 */
interface SaveMessageDraftUseCase {
    suspend operator fun invoke(messageDraft: MessageDraft)
}

class SaveMessageDraftUseCaseImpl internal constructor(
    private val messageDraftRepository: MessageDraftRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SaveMessageDraftUseCase {
    override suspend operator fun invoke(messageDraft: MessageDraft): Unit = withContext(dispatcher.io) {
        messageDraftRepository.saveMessageDraft(messageDraft)
    }
}
