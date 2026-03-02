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

import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.cells.domain.model.Owner
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity

/**
 * Use case to get the list of owners for a given conversation.
 */
public interface GetOwnersUseCase {
    public suspend operator fun invoke(
        conversationId: String?
    ): GetOwnersUseCaseResult
}

/**
 * Implementation of [GetOwnersUseCase] that retrieves the list of owners by fetching the conversation members
 * and mapping their user details to [Owner] objects. The use case returns a [GetOwnersUseCaseResult]
 * containing a list of [Owner] objects on success, or a [CoreFailure] on failure.
 *
 * @param usersRepository The repository to fetch the user details for the conversation member IDs.
 */
internal class GetOwnersUseCaseImpl(
    private val usersRepository: CellUsersRepository,
) : GetOwnersUseCase {

    override suspend operator fun invoke(
        conversationId: String?
    ): GetOwnersUseCaseResult {
        val result = if (conversationId == null) {
            usersRepository.getUsers()
        } else {
            val qualifiedConversationId = conversationId.toQualifiedIdOrNull()
                ?: return GetOwnersUseCaseResult.Failure(CoreFailure.Unknown(IllegalArgumentException("conversation_id_missing")))
            usersRepository.getConversationMemberDetails(qualifiedConversationId)
        }

        val owners = result.map { members ->
            members.map { userDetails ->
                Owner(
                    id = UserId(userDetails.id.value, userDetails.id.domain),
                    name = userDetails.name,
                    handle = userDetails.handle,
                    accentId = userDetails.accentId,
                    teamId = userDetails.team?.let { TeamId(it) },
                    previewPicture = userDetails.previewAssetId?.let { UserAssetId(it.value, it.domain) },
                    completePicture = userDetails.completeAssetId?.let { UserAssetId(it.value, it.domain) }
                )
            }
        }

        return when (owners) {
            is Either.Right -> GetOwnersUseCaseResult.Success(owners.value)
            is Either.Left -> GetOwnersUseCaseResult.Failure(owners.value)
        }
    }

    private fun String?.toQualifiedIdOrNull(): QualifiedIDEntity? {
        val sanitized = this?.trim().orEmpty()
        if (sanitized.isEmpty()) return null
        val value = sanitized.substringBefore('@')
        val domain = sanitized.substringAfter('@', missingDelimiterValue = "")
        return QualifiedIDEntity(value = value, domain = domain)
    }
}

public sealed class GetOwnersUseCaseResult {
    public data class Success(val owners: List<Owner>) : GetOwnersUseCaseResult()
    public data class Failure(val failure: CoreFailure) : GetOwnersUseCaseResult()
}
