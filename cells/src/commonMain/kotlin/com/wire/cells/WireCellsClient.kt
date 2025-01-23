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
package com.wire.cells

import com.wire.cells.s3.CellsS3Client
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.Path

class WireCellsClient(
    private val s3Client: CellsS3Client,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend fun list(cellName: String): List<String> {
        return withContext(dispatchers.io) {
            s3Client.list(cellName)
        }
    }

    suspend fun upload(cellName: String, fileName: String, path: Path, onProgressUpdate: (Long) -> Unit) {
        return withContext(dispatchers.io) {
            s3Client.upload(cellName, fileName, path, onProgressUpdate)
        }
    }

    suspend fun delete(cellName: String, fileName: String) {
        withContext(dispatchers.io) {
            s3Client.delete(cellName, fileName)
        }
    }
}
