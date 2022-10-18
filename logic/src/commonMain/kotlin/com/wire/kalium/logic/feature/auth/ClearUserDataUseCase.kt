package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.di.UserStorage

interface ClearUserDataUseCase {
    suspend operator fun invoke()
}

internal class ClearUserDataUseCaseImpl internal constructor(
    private val userStorage: UserStorage
) : ClearUserDataUseCase {

    override suspend operator fun invoke() {
        clearUserStorage()
    }

    private fun clearUserStorage() {
        userStorage.database.nuke()
        // exclude clientId clear from this step
        userStorage.preferences.clear()
    }
}
