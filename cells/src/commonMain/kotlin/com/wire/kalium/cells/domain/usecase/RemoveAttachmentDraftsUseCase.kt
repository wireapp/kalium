/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId

public interface RemoveAttachmentDraftsUseCase {
    /**
     * Removes the draft attachment from conversation.
     * If the attachment is in the process of uploading, the upload will be cancelled.
     * If the attachment is already uploaded, the attachment draft will be removed from the server.
     *
     * @param uuid UUID of the attachment
     * @return [Either] with [Unit] or [CoreFailure]
     */
    public suspend operator fun invoke(conversationId: ConversationId)
}

internal class RemoveAttachmentDraftsUseCaseImpl internal constructor(
    private val attachmentsRepository: MessageAttachmentDraftRepository,
) : RemoveAttachmentDraftsUseCase {

    override suspend fun invoke(conversationId: ConversationId) =
        attachmentsRepository.removeAttachmentDrafts(conversationId)

}
