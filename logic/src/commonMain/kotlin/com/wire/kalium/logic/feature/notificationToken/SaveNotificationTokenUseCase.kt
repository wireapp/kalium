/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.first

/**
 * Saves the push notification token for the users registered in the device.
 */
interface SaveNotificationTokenUseCase {
    /**
     * @param token the push notification token
     * @param type the type being ie: "GCM, APNS"
     * @param applicationId the application id (ie, internal) for which the token is valid
     * @return the [Result] with the result of the operation
     */
    suspend operator fun invoke(token: String, type: String, applicationId: String): Result
}

internal class SaveNotificationTokenUseCaseImpl(
    private val notificationTokenRepository: NotificationTokenRepository,
    private val observeValidAccounts: ObserveValidAccountsUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : SaveNotificationTokenUseCase {

    override suspend operator fun invoke(
        token: String,
        type: String,
        applicationId: String
    ): Result =
        notificationTokenRepository.persistNotificationToken(token, type, applicationId).fold({
            Result.Failure.Generic(it)
        }, {
            // we need to update FirebaseFlag for each user, so it will be updated on BE side too
            observeValidAccounts()
                .first()
                .forEach { (selfUser, _) ->
                    userSessionScopeProvider.getOrCreate(selfUser.id)
                        .pushTokenRepository
                        .setUpdateFirebaseTokenFlag(true)
                }
            Result.Success
        })

}

sealed class Result {
    data object Success : Result()
    sealed class Failure : Result() {
        data class Generic(val failure: StorageFailure) : Failure()
    }
}
