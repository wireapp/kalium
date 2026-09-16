/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.cells.domain.model.pdfPreviewUrl
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map

public fun interface GetPdfPreviewUrlUseCase {
    /**
     * Use case to get the pre-signed URL of the PDF rendition the backend generates for a
     * [com.wire.kalium.cells.domain.model.Node] it can convert (documents, presentations,
     * spreadsheets). It allows displaying such a document without an editor.
     *
     * @param nodeUuid The unique identifier of the node.
     * @return the URL of the PDF rendition, or null when the backend has none — the rendition is
     * still processing, failed, or the node type is not convertible.
     */
    public suspend operator fun invoke(nodeUuid: String): Either<CoreFailure, String?>
}

internal class GetPdfPreviewUrlUseCaseImpl(
    private val repository: CellsRepository
) : GetPdfPreviewUrlUseCase {

    override suspend fun invoke(nodeUuid: String): Either<CoreFailure, String?> =
        repository.getNode(nodeUuid).map { it.previews.pdfPreviewUrl() }
}
