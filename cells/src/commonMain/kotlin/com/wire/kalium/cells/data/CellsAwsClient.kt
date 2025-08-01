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

import com.wire.kalium.cells.data.model.CellNodeDTO
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import okio.Path
import okio.Sink

internal interface CellsAwsClient {
    suspend fun download(objectKey: String, outFileSink: Sink, onProgressUpdate: (Long) -> Unit)
    suspend fun upload(path: Path, node: CellNodeDTO, onProgressUpdate: (Long) -> Unit)
    suspend fun getPreSignedUrl(objectKey: String): String
}

internal expect fun cellsAwsClient(
    credentials: CellsCredentials?,
    sessionManager: SessionManager,
    accessTokenApi: AccessTokenApi
): CellsAwsClient
