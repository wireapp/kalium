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

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.id.ConversationId

public interface PublishAttachmentsUseCase {
    /**
     * For TESTING purposes only.
     * Use case for publishing all draft attachments and creating public URLs.
     */
    public suspend operator fun invoke(conversationId: ConversationId): Either<NetworkFailure, List<String>>
}

internal class PublishAttachmentsUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
    private val repository: MessageAttachmentDraftRepository,
) : PublishAttachmentsUseCase {

    @Suppress("ReturnCount")
    override suspend fun invoke(conversationId: ConversationId): Either<NetworkFailure, List<String>> {

        val publicUrls = mutableListOf<String>()
        val attachments = repository.getAll(conversationId).getOrNull()

        attachments?.forEach { attachment ->

            cellsRepository.publishDraft(attachment.uuid).onFailure {
                return Either.Left(it)
            }

            cellsRepository.getPublicUrl(attachment.uuid, attachment.fileName)
                .onSuccess {
                    publicUrls.add(it)
                }
                .onFailure {
                    return Either.Left(it)
                }

            repository.remove(attachment.uuid)
        }

        return Either.Right(publicUrls.toList())
    }
}
