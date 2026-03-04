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

import com.wire.kalium.cells.data.FileFilters
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.Owner
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserId

/**
 * Use case to get the list of owners for a given conversation and an optional search query.
 */
public interface GetOwnersUseCase {
    public suspend operator fun invoke(
        conversationId: String?,
        query: String = "",
    ): GetOwnersUseCaseResult
}

/**
 * Implementation of [GetOwnersUseCase] that retrieves the list of owners by fetching the nodes for the given conversation and query,
 * and then mapping the owner user IDs to their corresponding user details.
 * The resulting list of owners is distinct by their user ID.
 * The use case returns a [GetOwnersUseCaseResult] containing a list of [Owner] objects on success, or a [CoreFailure] on failure.
 *
 * @param cellsRepository The repository to fetch the nodes for the given conversation and query.
 * @param usersRepository The repository to fetch the user details for the owner user IDs.
 */
internal class GetOwnersUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val usersRepository: CellUsersRepository,
) : GetOwnersUseCase {

    override suspend operator fun invoke(
        conversationId: String?,
        query: String
    ): GetOwnersUseCaseResult {
        val result = cellsRepository.getNodesByPath(
            query = query,
            path = conversationId ?: "",
            fileFilters = FileFilters()
        ).flatMap { nodes ->
            usersRepository.getUsers().map { userDetailsEntities ->
                val ownerMap = mutableMapOf<String, Owner>()

                userDetailsEntities.forEach { userDetails ->
                    val owner = Owner(
                        id = UserId(userDetails.id.value, userDetails.id.domain),
                        name = userDetails.name,
                        handle = userDetails.handle,
                        accentId = userDetails.accentId,
                        teamId = userDetails.team?.let { TeamId(it) },
                        previewPicture = userDetails.previewAssetId?.let { UserAssetId(it.value, it.domain) },
                        completePicture = userDetails.completeAssetId?.let { UserAssetId(it.value, it.domain) }
                    )
                    ownerMap[userDetails.id.toString()] = owner
                }

                nodes.mapNotNull { node ->
                    node.ownerUserId?.let { ownerMap[it] }
                }.distinctBy { it.id }
            }
        }

        return when (result) {
            is Either.Right -> GetOwnersUseCaseResult.Success(result.value)
            is Either.Left -> GetOwnersUseCaseResult.Failure(result.value)
        }
    }
}

public sealed class GetOwnersUseCaseResult {
    public data class Success(val owners: List<Owner>) : GetOwnersUseCaseResult()
    public data class Failure(val failure: CoreFailure) : GetOwnersUseCaseResult()
}
