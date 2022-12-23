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

    override suspend operator fun invoke(token: String, type: String, applicationId: String): Result =
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
    object Success : Result()
    sealed class Failure : Result() {
        class Generic(val failure: StorageFailure) : Failure()
    }
}
