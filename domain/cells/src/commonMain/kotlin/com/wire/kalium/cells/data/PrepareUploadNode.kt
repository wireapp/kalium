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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import kotlin.uuid.Uuid

/** Reserves paths within one batch, including backend-proposed collision names. */
internal suspend fun CellsRepository.prepareUploadNode(
    requestedPath: String,
    size: Long,
    reserved: Set<String> = emptySet(),
): Either<NetworkFailure, CellNode> {
    var candidate = requestedPath
    var suffix = 1
    while (true) {
        while (candidate in reserved) {
            candidate = numberedPath(requestedPath, suffix++)
        }
        val checked = preCheck(candidate)
        if (checked is Either.Left) return checked
        val path = when (val result = (checked as Either.Right).value) {
            is PreCheckResult.FileExists -> result.nextPath
            PreCheckResult.Success -> candidate
        }
        if (path !in reserved) {
            return Either.Right(CellNode(Uuid.random().toString(), Uuid.random().toString(), path, size = size, isDraft = true))
        }
        candidate = path
    }
}

private fun numberedPath(path: String, suffix: Int): String {
    val directory = path.substringBeforeLast('/')
    val name = path.substringAfterLast('/')
    val stem = name.substringBeforeLast('.', name)
    val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    return "$directory/$stem ($suffix)$extension"
}
