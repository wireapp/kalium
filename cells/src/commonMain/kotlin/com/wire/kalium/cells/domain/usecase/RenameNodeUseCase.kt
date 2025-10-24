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
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.mapLeft

public interface RenameNodeUseCase {
    public suspend operator fun invoke(uuid: String, path: String, newName: String): Either<RenameNodeFailure, Unit>
}

internal class RenameNodeUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
) : RenameNodeUseCase {

    @Suppress("ReturnCount")
    override suspend fun invoke(uuid: String, path: String, newName: String): Either<RenameNodeFailure, Unit> {

        val targetPath = "${path.substringBeforeLast("/")}/$newName"

        val preCheck = cellsRepository.preCheck(targetPath).getOrElse {
            return RenameNodeFailure.Other(it).left()
        }

        if (preCheck is PreCheckResult.FileExists) {
            return RenameNodeFailure.FileAlreadyExists.left()
        }

        return cellsRepository.renameNode(uuid = uuid, path = path, targetPath = targetPath)
            .flatMap {
                attachmentsRepository.updateAssetPath(assetId = uuid, remotePath = targetPath)
            }
            .mapLeft {
                RenameNodeFailure.Other(it)
            }
    }
}

public sealed interface RenameNodeFailure {
    public object FileAlreadyExists : RenameNodeFailure
    public data class Other(val error: CoreFailure) : RenameNodeFailure
}
