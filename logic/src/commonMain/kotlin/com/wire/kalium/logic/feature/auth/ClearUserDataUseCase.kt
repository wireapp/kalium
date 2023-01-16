package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.di.UserStorage
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Clears the user data from the local storage, except for the client id
 */
interface ClearUserDataUseCase {
    suspend operator fun invoke()
}

internal class ClearUserDataUseCaseImpl internal constructor(
    private val userStorage: UserStorage
) : ClearUserDataUseCase {

    override suspend operator fun invoke() = withContext(KaliumDispatcherImpl.default) {
        clearUserStorage()
    }

    private fun clearUserStorage() {
        userStorage.database.nuke()
        // exclude clientId clear from this step
        userStorage.preferences.clear()
    }
}
