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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.CellAssetContent
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

public interface DeleteMessageAttachmentsUseCase {
    public suspend operator fun invoke(messageId: String, conversationId: ConversationId)
}

internal class DeleteMessageAttachmentsUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : DeleteMessageAttachmentsUseCase {

    override suspend fun invoke(messageId: String, conversationId: ConversationId) {

        attachmentsRepository.getAttachments(messageId, conversationId).map { attachments ->

            val paths = attachments.filterIsInstance<CellAssetContent>().mapNotNull { it.assetPath }

            if (paths.isNotEmpty()) {
                cellsRepository.deleteFiles(paths)
            }

            val localPaths = attachments.filterIsInstance<CellAssetContent>().mapNotNull { it.localPath }

            localPaths.forEach {
                fileSystem.delete(it.toPath())
            }
        }
    }
}
