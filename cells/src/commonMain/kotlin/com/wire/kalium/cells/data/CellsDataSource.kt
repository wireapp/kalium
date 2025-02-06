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
import com.wire.kalium.cells.data.model.toDto
import com.wire.kalium.cells.data.model.toModel
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okio.Path

internal class CellsDataSource internal constructor(
    private val cellsApi: CellsApi,
    private val awsClient: CellsAwsClient,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : CellsRepository {

    override suspend fun preCheck(node: CellNode): Either<NetworkFailure, PreCheckResult> {
        return withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.preCheck(node.path)
            }.map { result ->
                if (result.fileExists) {
                    PreCheckResult.FileExists(result.suggestedPath ?: node.path)
                } else {
                    PreCheckResult.Success
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun uploadFile(
        path: Path,
        node: CellNode,
        onProgressUpdate: (Long) -> Unit,
    ): Either<NetworkFailure, Unit> = withContext(dispatchers.io) {
        try {
            awsClient.upload(path, node.toDto(), onProgressUpdate)
            Either.Right(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Either.Left(NetworkFailure.ServerMiscommunication(e))
        }
    }

    override suspend fun getFiles(cellName: String): Either<NetworkFailure, List<CellNode>> =
        withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.getFiles(cellName)
            }.map { response ->
                response.nodes
                    .filterNot { it.isRecycleBin }
                    .map { it.toModel() }
            }
        }

    override suspend fun deleteFile(node: CellNode): Either<NetworkFailure, Unit> {
        return withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.delete(node.toDto())
            }
        }
    }

    override suspend fun publishDraft(node: CellNode): Either<NetworkFailure, Unit> {
        return withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.publishDraft(node.toDto())
            }
        }
    }

    override suspend fun cancelDraft(node: CellNode): Either<NetworkFailure, Unit> {
        return withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.cancelDraft(node.toDto())
            }
        }
    }
}

internal inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> =
    when (val result = networkCall()) {
        is NetworkResponse.Success -> Either.Right(result.value)
        is NetworkResponse.Error -> Either.Left(NetworkFailure.ServerMiscommunication(result.kException))
    }
