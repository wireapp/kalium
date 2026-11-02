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
import com.wire.kalium.cells.diagnosticSummary
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageAttachment

public interface PublishAttachmentsUseCase {
    /**
     * Publishes the draft versions of the Cells assets in [attachments]; other attachments are ignored.
     * Returns the first failure if any publish fails.
     */
    public suspend operator fun invoke(attachments: List<MessageAttachment>): Either<NetworkFailure, Unit>
}

internal class PublishAttachmentsUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
) : PublishAttachmentsUseCase {

    @Suppress("ReturnCount")
    override suspend fun invoke(attachments: List<MessageAttachment>): Either<NetworkFailure, Unit> {
        kaliumLogger.i("Cells share stage=PUBLISH_START attachments=${attachments.size}")
        val drafts = mutableListOf<NodeIdAndVersion>()
        for (asset in attachments.filterIsInstance<CellAssetContent>()) {
            val result = cellsRepository.getNodeVersions(asset.id)
            if (result is Either.Left) {
                kaliumLogger.w("Cells share stage=VERSION_LOOKUP_FAILED failure=${result.value.diagnosticSummary()}")
                return result
            }
            val version = (result as Either.Right).value.firstOrNull { it.id == asset.versionId }
            kaliumLogger.i("Cells share stage=VERSION_STATE found=${version != null} draft=${version?.isDraft}")
            if (version == null) {
                return Either.Left(
                    NetworkFailure.ServerMiscommunication(IllegalStateException("Cells attachment version unavailable"))
                )
            }
            // Cells omits the proto3 Draft field when false. A matching published version is safe to retry.
            if (version.isDraft != true) continue
            drafts += NodeIdAndVersion(asset.id, asset.versionId)
        }
        return if (drafts.isEmpty()) Either.Right(Unit) else cellsRepository.publishDrafts(drafts)
            .onFailure { kaliumLogger.w("Cells share stage=PUBLISH_FAILED failure=${it.diagnosticSummary()}") }
            .onSuccess { kaliumLogger.i("Cells share stage=PUBLISH_COMPLETE drafts=${drafts.size}") }
    }
}
