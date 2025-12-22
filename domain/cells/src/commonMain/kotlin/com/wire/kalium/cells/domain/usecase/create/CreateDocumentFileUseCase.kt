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
package com.wire.kalium.cells.domain.usecase.create

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map

/**
 * Create a document file(.docx) in the wire cell server.
 */
public interface CreateDocumentFileUseCase {
    public suspend operator fun invoke(path: String): Either<CoreFailure, Unit>
}

internal class CreateDocumentFileUseCaseImpl(
    private val cellsRepository: CellsRepository,
) : CreateDocumentFileUseCase {
    override suspend fun invoke(path: String): Either<CoreFailure, Unit> =
        cellsRepository.createFile(path + EXTENSION, CONTENT_TYPE).map { }

    companion object {
        const val EXTENSION = ".docx"
        const val CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
