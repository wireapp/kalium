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
 * Create a spreadsheet file(.xlsx) in the wire cell server.
 */
public interface CreateSpreadsheetFileUseCase {
    public suspend operator fun invoke(path: String): Either<CoreFailure, Unit>
}

internal class CreateSpreadsheetFileUseCaseImpl(
    private val cellsRepository: CellsRepository,
) : CreateSpreadsheetFileUseCase {
    override suspend fun invoke(path: String): Either<CoreFailure, Unit> =
        cellsRepository.createFile(
            folderName = path + EXTENSION,
            contentType = CONTENT_TYPE,
            templateUuid = SPREADSHEET_TEMPLATE_UUID
        ).map { }

    companion object {
        const val EXTENSION = ".xlsx"
        const val CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val SPREADSHEET_TEMPLATE_UUID = "02-Microsoft Excel.xlsx"
    }
}
