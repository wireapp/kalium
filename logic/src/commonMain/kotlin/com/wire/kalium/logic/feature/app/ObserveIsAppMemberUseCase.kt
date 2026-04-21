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
package com.wire.kalium.logic.feature.app

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public interface ObserveIsAppMemberUseCase {
    /**
     * This use case is responsible for observing if an App is member of given conversation.
     * @param appId contains the ID of the App.
     * @param conversationId ID of the conversation App will be seen, added or removed.
     * @return a [Flow] of [ObserveIsAppMemberResult] with Success of a Qualified ID of App in User table or NULL or an error.
     */
    public suspend operator fun invoke(
        appId: QualifiedID,
        conversationId: ConversationId
    ): Flow<ObserveIsAppMemberResult>
}

internal class ObserveIsAppMemberUseCaseImpl internal constructor(
    private val appRepository: AppRepository
) : ObserveIsAppMemberUseCase {

    override suspend fun invoke(
        appId: QualifiedID,
        conversationId: ConversationId
    ): Flow<ObserveIsAppMemberResult> =
        appRepository.observeIsAppMember(
            appId = appId,
            conversationId = conversationId
        ).map {
            it.fold({ failure ->
                ObserveIsAppMemberResult.Failure(failure)
            }, { success ->
                ObserveIsAppMemberResult.Success(success)
            })
        }
}

public sealed class ObserveIsAppMemberResult {
    public data class Success(val userId: UserId?) : ObserveIsAppMemberResult()
    public data class Failure(val failure: StorageFailure) : ObserveIsAppMemberResult()
}
