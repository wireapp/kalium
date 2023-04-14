/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
interface E2EIRepository {

    suspend fun enrollE2EI()
    suspend fun getACMEDirectories(): Either<NetworkFailure, AcmeDirectoriesResponse>
    suspend fun getNewNonce(nonceUrl: String): Either<NetworkFailure, String>

    suspend fun createNewAccount(requestUrl: String, previousNonce: String): Either<NetworkFailure, String>
}

class E2EIRepositoryImpl(
    private val e2EIApi: E2EIApi,
    private val mlsClientProvider: MLSClientProvider
) : E2EIRepository {

    override suspend fun getACMEDirectories(): Either<NetworkFailure, AcmeDirectoriesResponse> =
        wrapApiRequest {
            e2EIApi.getDirectories()
        }

    override suspend fun enrollE2EI() {
        getACMEDirectories().onSuccess { directories ->
            mlsClientProvider.getE2EIClient().flatMap { e2eiClient ->
                e2eiClient.directoryResponse(Json.encodeToString(directories).encodeToByteArray())
                Either.Right(Unit)
            }
            getNewNonce(directories.newNonce).onSuccess {
                kaliumLogger.d("## newNonce -> $it")
                createNewAccount(directories.newAccount, it)
            }
        }
    }

    override suspend fun getNewNonce(nonceUrl: String): Either<NetworkFailure, String> =
        wrapApiRequest {
            e2EIApi.getNewNonce(nonceUrl)
        }

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun createNewAccount(requestUrl: String, previousNonce: String): Either<NetworkFailure, String> {
        val newAccountRequest = mlsClientProvider.getE2EIClient().flatMap {
            it.newAccountRequest(previousNonce).let {
                kaliumLogger.d("## \n\n\n\n\n\n\n\n\n ########## new account response -> $it")

                return@flatMap wrapApiRequest {
                    val response = e2EIApi.sendNewAccount(requestUrl, it)
                    response
                }
            }
        }
        return Either.Right("")
    }
}
